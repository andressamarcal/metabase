(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase.api.common :as api :refer [*current-user* *current-user-id* *current-user-permissions-set*]]
            [metabase.mbql
             [schema :as mbql.s]
             [util :as mbql.u]]
            [metabase.models
             [card :refer [Card]]
             [field :refer [Field]]
             [params :as params]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            [metabase.models.query.permissions :as query-perms]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware.fetch-source-query :as fetch-source-query]
            [metabase.query-processor.store :as qp.store]
            [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]
            [schema.core :as s]
            [toucan.db :as db]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                  query->gtap                                                   |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- all-table-ids [m]
  (set
   (reduce
    concat
    (mbql.u/match m
      (_ :guard (every-pred map? :source-table (complement :gtap?)))
      (let [recursive-ids (all-table-ids (dissoc &match :source-table))]
        (cons (:source-table &match) recursive-ids))))))

(defn- query->all-table-ids [query]
  (let [ids (all-table-ids query)]
    (when (seq ids)
      (qp.store/fetch-and-store-tables! ids)
      (set ids))))

(defn- table-should-have-segmented-permissions?
  "Determine whether we should apply segmented permissions for `table-or-table-id`."
  [table-id]
  (let [table (assoc (qp.store/table table-id) :db_id (u/get-id (qp.store/database)))]
    (and
     ;; User does not have full data access
     (not (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-query-path table)))
     ;; User does have segmented access
     (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-segmented-query-path table)))))

(defn- assert-one-gtap-per-table
  "Make sure all referenced Tables have at most one GTAP."
  [gtaps]
  (doseq [[table-id gtaps] (group-by :table_id gtaps)
          :when            (> (count gtaps) 1)]
    (throw (ex-info (str (tru "Found more than one group table access policy for user ''{0}''"
                              (:email @*current-user*)))
             {:table-id  table-id
              :gtaps     gtaps
              :user      *current-user-id*
              :group-ids (map :group_id gtaps)}))))

(defn- tables->gtaps [table-ids]
  (qp.store/cached [*current-user-id* table-ids]
    (let [group-ids (qp.store/cached *current-user-id*
                      (db/select-field :group_id PermissionsGroupMembership :user_id *current-user-id*))
          gtaps     (when (seq group-ids)
                      (db/select GroupTableAccessPolicy
                        :group_id [:in group-ids]
                        :table_id [:in table-ids]))]
      (when (seq gtaps)
        (assert-one-gtap-per-table gtaps)
        gtaps))))

(defn- query->table-id->gtap [query]
  {:pre [(some? *current-user-id*)]}
  (when-let [gtaps (some->> (query->all-table-ids query)
                            ((comp seq filter) table-should-have-segmented-permissions?)
                            tables->gtaps)]
    (u/key-by :table_id gtaps)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                Applying a GTAP                                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

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

(defn- gtap->parameters [{attribute-remappings :attribute_remappings}]
  (mapv (partial attr-remapping->parameter (:login_attributes @*current-user*))
        attribute-remappings))

(s/defn ^:private preprocess-source-query :- mbql.s/SourceQuery
  [source-query :- mbql.s/SourceQuery]
  (let [query        {:database (:id (qp.store/database))
                      :type     :query
                      :query    source-query}
        preprocessed (binding [api/*current-user-id* nil]
                       ((resolve 'metabase.query-processor/query->preprocessed) query))]
    (select-keys (:query preprocessed) [:source-query :source-metadata])))

(s/defn ^:private card-gtap->source
  [{card-id :card_id, :as gtap}]
  (update-in (fetch-source-query/card-id->source-query-and-metadata card-id)
             [:source-query :parameters]
             concat
             (gtap->parameters gtap)))

(defn- table-gtap->source [{table-id :table_id, :as gtap}]
  {:source-query {:source-table table-id, :parameters (gtap->parameters gtap)}})

(defn- gtap->source [{card-id :card_id, :as gtap}]
  {:pre [gtap]}
  (let [source ((if card-id card-gtap->source table-gtap->source) gtap)]
    (preprocess-source-query source)))

(s/defn ^:private gtap->perms-set :- #{perms/ObjectPath}
  "Calculate the set of permissions needed to run the query associated with a GTAP; this set of permissions is excluded
  during the normal QP perms check.

  Background: when applying GTAPs, we don't want the QP perms check middleware to throw an Exception if the Current
  User doesn't have permissions to run the underlying GTAP query, which will likely be greater than what they actually
  have. (For example, a User might have segmented query perms for Table 15, which is why we're applying a GTAP in the
  first place; the actual perms required to normally run the underlying GTAP query is more likely something like
  *full* query perms for Table 15.) The QP perms check middleware subtracts this set from the set of required
  permissions, allowing the user to run their GTAPped query."
  [{card-id :card_id, table-id :table_id}]
  (if card-id
    (qp.store/cached card-id
      (query-perms/perms-set (db/select-one-field :dataset_query Card :id card-id), :throw-exceptions? true))
    #{(perms/table-query-path (Table table-id))}))

(defn- gtaps->perms-set [gtaps]
  (set (mapcat gtap->perms-set gtaps)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                   Middleware                                                   |
;;; +----------------------------------------------------------------------------------------------------------------+

;;; ------------------------------------------ apply-row-level-permissions -------------------------------------------

(defn- apply-gtap [m gtap]
  (merge
   (dissoc m :source-table :source-query)
   (gtap->source gtap)))

(defn- apply-gtaps [m table-id->gtap]
  ;; replace maps that have `:source-table` key and a matching entry in `table-id->gtap`, but do not have `:gtap?` key
  (mbql.u/replace m
    (_ :guard (every-pred map? (complement :gtap?) :source-table #(get table-id->gtap (:source-table %))))
    (let [updated             (apply-gtap &match (get table-id->gtap (:source-table &match)))
          ;; now recursively apply gtaps anywhere else they might exist at this level, e.g. `:joins`
          recursively-updated (merge
                               (select-keys updated [:source-table :source-query])
                               (apply-gtaps (dissoc updated :source-table :source-query) table-id->gtap))]
      ;; add a `:gtap?` key next to every `:source-table` key so when we do a second pass after adding JOINs they
      ;; don't get processed again
      (mbql.u/replace recursively-updated
        (_ :guard (every-pred map? :source-table))
        (assoc &match :gtap? true)))))

(defn- apply-row-level-permissions* [query]
  (if-let [table-id->gtap (when *current-user-id*
                            (query->table-id->gtap query))]
    (-> query
        (apply-gtaps table-id->gtap)
        (update :gtap-perms (comp set concat) (gtaps->perms-set (vals table-id->gtap))))
    query))

(defn apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned. Will return the original query if there are no segmented permissions found"
  [qp]
  (comp qp apply-row-level-permissions*))