(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase.api.common :refer [*current-user-id* *current-user* *current-user-permissions-set*]]
            [metabase.mbql.schema :as mbql.s]
            [metabase.models
             [card :refer [Card]]
             [database :as database]
             [field :refer [Field]]
             [params :as params]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            [metabase.models.query.permissions :as query-perms]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.util :as qputil]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [puppetlabs.i18n.core :refer [tru]]
            [schema.core :as s]
            [toucan.db :as db]
            [metabase.api.common :as api]
            [metabase.query-processor.middleware.resolve-fields :as resolve-fields]
            [metabase.mbql.util :as mbql.u]))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                     Fetching Appropriate GTAPs for a Table                                     |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- gtap-for-table [outer-query table-or-table-id]
  (let [groups (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id *current-user-id*))]
    ;; check that user is in a group
    (when-not (seq groups)
      (throw (RuntimeException. (str (tru "User with email ''{0}'' is not a member of any group"
                                          (:email @*current-user*))))))
    ;; ok, now fetch GTAP(s). More than one GTAP = error
    (let [[gtap & more-gtaps] (db/select GroupTableAccessPolicy
                                :group_id [:in groups]
                                :table_id (u/get-id table-or-table-id))]
      (if (seq more-gtaps)
        (throw (RuntimeException. (str (tru "Found more than one group table access policy for user ''{0}''"
                                            (:email @*current-user*)))))
        gtap))))

(defn- target->type
  "Attempt to expand `maybe-field` to find its `id`. This might not be a field and instead a template tag or something
  else. Return the field id if we can, otherwise nil"
  [[_ maybe-field]]
  (when-let [field-id (u/ignore-exceptions (params/field-form->id maybe-field))]
    (db/select-one-field :base_type Field :id field-id)))

(defn- attr-value->param-value
  "Take an `attr-value` with a desired `target-type` and coerce to that type if need be. If not type is given or it's
  already correct, return the original `attr-value`"
  [target-type attr-value]
  (let [attr-string? (string? attr-value)]
    (cond
      ;; If the attr-value is a string and the target type is integer, parse it as a long
      (and attr-string? (isa? target-type :type/Integer))
      (Long/parseLong attr-value)
      ;; If the attr-value is a string and the target type is float, parse it as a double
      (and attr-string? (isa? target-type :type/Float))
      (Double/parseDouble attr-value)
      ;; No need to parse it if the type isn't numeric or if it's already a number
      :else
      attr-value)))

(defn- attr-remapping->parameter [login-attributes [attr-name target]]
  (let [attr-value       (get login-attributes attr-name ::not-found)
        maybe-field-type (target->type target)]
    (when (= attr-value ::not-found)
      (throw (IllegalArgumentException. (str (tru "Query requires user attribute `{0}`" (name attr-name))))))
    {:type   :category
     :target target
     :value  (attr-value->param-value maybe-field-type attr-value)}))

(defn- gtap->database-id [{:keys [card_id table_id] :as gtap}]
  (if card_id
    database/virtual-id
    (db/select-one-field :db_id Table :id table_id)))

(defn- gtap->source-table [{:keys [card_id table_id] :as gtap}]
  (if card_id
    (str "card__" card_id)
    table_id))

(s/defn ^:private gtap->perms-set :- #{perms/ObjectPath}
  "Calculate the set of permissions needed to run the query associated with a GTAP; this set of permissions is excluded
  during the normal QP perms check.

  Background: when applying GTAPs, we don't want the QP perms check middleware to throw an Exception if the Current
  User doesn't have permissions to run the underlying GTAP query, which will likely be greater than what they actually
  have. (For example, a User might have segmented query perms for Table 15, which is why we're applying a GTAP in the
  first place; the actual perms required to normally run the underlying GTAP query is more likely something like
  *full* query perms for Table 15.) The QP perms check middleware subtracts this set from the set of required
  permissions, allowing the user to run their GTAPped query."
  [{:keys [card_id table_id]}]
  (if card_id
    (query-perms/perms-set (db/select-one-field :dataset_query Card :id card_id), :throw-exceptions? true)
    #{(perms/table-query-path (Table table_id))}))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          Should a Query Get GTAPped?                                           |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- table-should-have-segmented-permissions?
  "Determine whether we should apply segmented permissions for `table-or-table-id`.

    (table-should-have-segmented-permissions? outer-query) ; -> true"
  [table-or-table-id]
  (boolean
   ;; Check whether the query uses a source Table (e.g., whether it is an MBQL query)
   (when-let [id (and *current-user-id* table-or-table-id (u/get-id table-or-table-id))]
     (let [table (db/select-one ['Table :id :db_id :schema] :id id)]
       (and
        ;; User does not have full data access
        (not (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-query-path table)))
        ;; User does have segmented access
        (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-segmented-query-path table)))))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                           Applying GTAPs to a Query                                            |
;;; +----------------------------------------------------------------------------------------------------------------+

;;; ------------------------------------------ apply-row-level-permissions -------------------------------------------

(s/defn ^:private apply-row-level-permissions-from-gtap :- mbql.s/Query
  {:arglists '([outer-query gtap])}
  [outer-query {:keys [attribute_remappings] :as gtap}]
  (-> outer-query
      (assoc :database    (gtap->database-id gtap)
             :type        :query
             :gtap-perms  (gtap->perms-set gtap))
      (assoc-in [:query :source-table] (gtap->source-table gtap))
      (update :parameters into (map (partial attr-remapping->parameter (:login_attributes @*current-user*))
                                    attribute_remappings))))

(defn- query->gtap
  "Return the GTAP that should be applied to a `query`, or `nil` if none should be applied."
  [outer-query]
  ;; `query->source-table-id` will throw an Exception if it encounters an unresolved source query, i.e. a `card__id`
  ;; form; we don't support loading GTAPs for those at this point in time, so we can go ahead and ignore that
  ;; Exception.
  (when-let [source-table-id (u/ignore-exceptions
                               (mbql.u/query->source-table-id outer-query))]
    (and (table-should-have-segmented-permissions? source-table-id)
         (gtap-for-table outer-query source-table-id))))

(defn- apply-row-level-permissions* [outer-query]
  (if-let [gtap (query->gtap outer-query)]
    (apply-row-level-permissions-from-gtap outer-query gtap)
    outer-query))

(defn apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned. Will return the original query if there are no segmented permissions found"
  [qp]
  (comp qp apply-row-level-permissions*))


;;; ------------------------------------- apply-row-level-permissions-for-joins --------------------------------------

(defn- preprocess-and-resolve-fields [query]
  (u/prog1 (binding [api/*current-user-id* nil]
             ((resolve 'metabase.query-processor/query->preprocessed) query))
    ;; have to make sure the Fields from the Join query we create are present in the store because we're doing this
    ;; after the middleware step that normally resolves Fields
    ((resolve-fields/resolve-fields identity) <>)))

(s/defn create-join-query :- mbql.s/JoinQueryInfo
  "Create a join query with a new GTAP query used for the join and using the original join Table data from `join-table`"
  [join-table :- mbql.s/JoinTableInfo, gtap, {:keys [database] :as orig-query}]
  (merge
   (select-keys join-table [:table-id :join-alias :fk-field-id :pk-field-id])
   {:join-alias (:join-alias join-table)
    :query      (preprocess-and-resolve-fields
                 {:database   (u/get-id database)
                  :type       :query
                  :query      {:source-table (gtap->source-table gtap)}
                  :parameters (mapv (partial attr-remapping->parameter (:login_attributes @*current-user*))
                                    (:attribute_remappings gtap))})}))

(defn- apply-row-level-permissions-for-joins* [query]
  (let [join-tables     (-> query :query :join-tables)
        table-ids->gtap (into {} (for [{:keys [table-id]} join-tables
                                       :when (table-should-have-segmented-permissions? table-id)]
                                   [table-id (gtap-for-table query table-id)]))]
    (if (seq table-ids->gtap)
      (-> query
          (update :gtap-perms into (mapcat gtap->perms-set (vals table-ids->gtap)))
          (assoc-in [:query :join-tables] (vec (for [{:keys [table-id], :as jt} join-tables]
                                                 (if-let [gtap (get table-ids->gtap table-id)]
                                                   (create-join-query jt gtap query)
                                                   jt)))))
      query)))

(defn apply-row-level-permissions-for-joins
  "Looks in the `:join-tables` of the query for segmented permissions. If any are found, swap them for a gtap
  otherwise return the original query"
  [qp]
  (comp qp apply-row-level-permissions-for-joins*))
