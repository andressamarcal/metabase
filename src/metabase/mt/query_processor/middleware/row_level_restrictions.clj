(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
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
            [metabase.mt.query-processor.middleware.util :as mt-util]
            [metabase.query-processor :as qp]
            [metabase.query-processor
             [interface :as qpi]
             [util :as qputil]]
            [metabase.query-processor.middleware.log :as log-query]
            [metabase.query-processor.middleware.binning :as binning]
            [metabase.util.schema :as su]
            [puppetlabs.i18n.core :refer [tru]]
            [schema.core :as s]
            [toucan.db :as db]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                      Determining Source Table of a Query                                       |
;;; +----------------------------------------------------------------------------------------------------------------+

(s/defn ^:private table->id :- (s/maybe su/IntGreaterThanZero)
  "Return the ID of a Table, regardless of the possible format it's currently in.
  Depending on which stage of query expansion we're at, keys like `:source-table` might either still be a raw Table ID
  or may have already been 'resolved' and replaced with the full Table object. Additional, there are some differences
  between `:join-tables` and `:source-table` using `:id` vs `:table-id`. These inconsistencies are annoying, but
  luckily this function exists to handle any possible case and always return the ID. Can return nil if `maybe-table`
  is a card. The user might not have access to the underlying table if they have access to a narrowed view via the card."
  [maybe-table]

  (when-not maybe-table
    (throw (Exception. (str (tru "Error: table is nil")))))

  (cond
    ;; This is a card form like card__17
    (string? maybe-table)
    nil

    (integer? maybe-table)
    maybe-table

    :else
    (or (:id maybe-table)
        (:table-id maybe-table))))

(s/defn ^:private query->source-table-id :- (s/maybe su/IntGreaterThanZero)
  "Return the ID of the source Table for this `query`, if this is an MBQL query."
  [{inner-query :query, :as outer-query}]
  (if-let [source-query (qputil/get-normalized outer-query :source-query)]
    (recur (assoc outer-query :query source-query))
    (some-> (qputil/get-normalized inner-query :source-table) table->id)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                     Fetching Appropriate GTAPs for a Table                                     |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- gtap-for-table [outer-query table-or-table-id]
  (let [groups (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id (:user outer-query)))]
    (if (seq groups)
      (let [[gtap & more-gtaps] (db/select GroupTableAccessPolicy :group_id [:in groups]
                                           :table_id (u/get-id table-or-table-id))]
        (if (seq more-gtaps)
          (throw (RuntimeException. (str (tru "Found more than one group table access policy for user ''{0}''"
                                              (get-in outer-query [:user :email])))))
          gtap))
      (throw (RuntimeException. (str (tru "User with email ''{0}'' is not a member of any group"
                                          (get-in outer-query [:user :email]))))))))

(defn- target->type
  "Attempt to expand `maybe-field` to find its `id`. This might not be a field and instead a template tag or something
  else. Return the field id if we can, otherwise nil"
  [[_ maybe-field]]
  (when-let [field-id (params/field-form->id maybe-field)]
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
  ;; defaults attr-value to "" because if it's nil the parameter is ignored
  ;; TODO: maybe we should just throw an exception
  (let [attr-value       (get login-attributes attr-name "")
        maybe-field-type (target->type target)]
    {:type   "category"
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
    (query-perms/perms-set (db/select-one-field :dataset_query Card :id card_id) :throw-exceptions)
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

(defn- apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned. Will return the original query if there are no segmented permissions found"
  [outer-query]
  (if-let [{:keys [attribute_remappings] :as gtap} (and (table-should-have-segmented-permissions? (query->source-table-id outer-query))
                                                        (gtap-for-table outer-query (query->source-table-id outer-query)))]
    (let [login-attributes (qputil/get-in-normalized outer-query [:user :login-attributes])]
      (-> outer-query
          (assoc :database    (gtap->database-id gtap)
                 :type        :query
                 :gtap-perms  (gtap->perms-set gtap))
          ;; We need to dissoc the source-table before associng a new one. Due to normalization, it's possible that
          ;; we'll have `:source_table` and `:source-table` after this next line, removing it ensures we'll at least not
          ;; have more source table entries after the next line
          (update :query qputil/dissoc-normalized :source-table)
          (update :query assoc :source-table (gtap->source-table gtap))
          (update :parameters into (map #(attr-remapping->parameter login-attributes %)
                                        attribute_remappings))))
    outer-query))

(defn create-join-query
  "Create a `JoinQuery` with a new GTAP query used for the join and using the original `JoinTable` data from
  `join-table`"
  [join-table gtap {:keys [database user user-attributes] :as orig-query}]
  (let [new-query-params  (map #(attr-remapping->parameter (:login_attributes user) %) (:attribute_remappings gtap))]
    (qpi/map->JoinQuery
     (-> join-table
         (select-keys [:table-id :join-alias :source-field :pk-field :schema])
         (assoc :query (qp/expand {:database        (u/get-id database)
                                   :type            :query
                                   :user            user
                                   :user-attributes user-attributes
                                   :query           {:source-table (gtap->source-table gtap)}
                                   :parameters      new-query-params}))))))

(defn- apply-row-level-permissions-for-joins
  "Looks in the `:join-tables` of the query for segmented permissions. If any are found, swap them for a gtap
  otherwise return the original query"
  [query]
  (let [join-tables     (qputil/get-in-query query [:join-tables])
        table-ids->gtap (into {} (for [{:keys [table-id]} join-tables
                                       :when (table-should-have-segmented-permissions? table-id)]
                                   [table-id (gtap-for-table query table-id)]))]
    (if (seq table-ids->gtap)
      (-> query
          (update :gtap-perms into (mapcat gtap->perms-set (vals table-ids->gtap)))
          (qputil/assoc-in-query [:join-tables] (for [{:keys [table-id] :as jt} join-tables]
                                                  (if-let [gtap (get table-ids->gtap table-id)]
                                                    (create-join-query jt gtap query)
                                                    jt))))
      query)))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                 The Middleware Itself & Logic for Injecting It                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- inject-middleware
  [middleware]
  (fn [qp]
    (comp qp middleware)))

(defn update-qp-pipeline
  "Update the query pipeline atom to include the row level restrictions middleware. Intended to be called on startup."
  []
  (mt-util/update-qp-pipeline #'log-query/log-initial-query
                              (inject-middleware #'apply-row-level-permissions))
  (mt-util/update-qp-pipeline #'binning/update-binning-strategy
                              (inject-middleware #'apply-row-level-permissions-for-joins)))
