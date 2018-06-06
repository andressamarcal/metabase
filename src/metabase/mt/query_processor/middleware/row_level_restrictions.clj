(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.models
             [card :refer [Card]]
             [database :as database]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            [metabase.models.query.permissions :as query-perms]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware.log :as log-query]
            [metabase.query-processor.util :as qputil]
            [metabase.util.schema :as su]
            [puppetlabs.i18n.core :refer [tru]]
            [schema.core :as s]
            [toucan.db :as db]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                      Determining Source Table of a Query                                       |
;;; +----------------------------------------------------------------------------------------------------------------+

(s/defn ^:private table->id :- su/IntGreaterThanZero
  "Return the ID of a Table, regardless of the possible format it's currently in.
  Depending on which stage of query expansion we're at, keys like `:source-table` might either still be a raw Table ID
  or may have already been 'resolved' and replaced with the full Table object. Additional, there are some differences
  between `:join-tables` and `:source-table` using `:id` vs `:table-id`. These inconsistencies are annoying, but
  luckily this function exists to handle any possible case and always return the ID."
  [table]
  (when-not table
    (throw (Exception. (str (tru "Error: table is nil")))))
  (or (when (integer? table) table)
      (:id table)
      (:table-id table)))

(s/defn ^:private query->source-table-id :- (s/maybe su/IntGreaterThanZero)
  "Return the ID of the source Table for this `query`, if this is an MBQL query."
  [{inner-query :query, :as outer-query}]
  (if-let [source-query (qputil/get-normalized outer-query :source-query)]
    (recur (assoc outer-query :query source-query))
    (some-> (qputil/get-normalized inner-query :source-table) table->id)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                     Fetching Appropriate GTAPs for a Table                                     |
;;; +----------------------------------------------------------------------------------------------------------------+


(defn- gtap-for-table [outer-query]
  (let [groups (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id (:user outer-query)))]
    (if (seq groups)
      (let [[gtap & more-gtaps] (db/select GroupTableAccessPolicy :group_id [:in groups]
                                           :table_id (query->source-table-id outer-query))]
        (if (seq more-gtaps)
          (throw (RuntimeException. (str (tru "Found more than one group table access policy for user ''{0}''"
                                              (get-in outer-query [:user :email])))))
          gtap))
      (throw (RuntimeException. (str (tru "User with email ''{0}'' is not a member of any group"
                                          (get-in outer-query [:user :email]))))))))

(defn- attr-remapping->parameter [login-attributes [attr-name target]]
  ;; defaults attr-value to "" because if it's nil the parameter is ignored
  ;; TODO: maybe we should just throw an exception
  (let [attr-value (get login-attributes attr-name "")]
    {:type "category", :value attr-value, :target target}))

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
;;; |                                           Applying GTAPs to a Query                                            |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned"
  [qp outer-query]
  (if-let [{:keys [attribute_remappings] :as gtap} (gtap-for-table outer-query)]
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
                                         attribute_remappings))
          qp))
    (qp outer-query)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          Should a Query Get GTAPped?                                           |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- query-should-have-segmented-permissions?
  "Determine whether we should apply segmented permissions for `query`.

    (query-should-have-segmented-permissions? outer-query) ; -> true"
  [query]
  (boolean
   (when *current-user-id*
     ;; Check whether the query uses a source Table (e.g., whether it is an MBQL query)
     (when-let [source-table-id (query->source-table-id query)]
       (let [table (db/select-one ['Table :id :db_id :schema] :id source-table-id)]
         (cond
           (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-query-path table))
           false

           (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-segmented-query-path table))
           true

           :else
           (throw (Exception.
                   (str (tru "Invalid state: user does not have either full or segmented query permissions!"))))))))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                 The Middleware Itself & Logic for Injecting It                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn maybe-apply-row-level-permissions
  "Applies row level permissions if the user has segmented permissions. If the user has full permissions, the data
  just passes through with no changes"
  [qp]
  (fn [query]
    (if (query-should-have-segmented-permissions? query)
      (apply-row-level-permissions qp query)
      (qp query))))

(defn- vec-index-of [pred coll]
  (reduce (fn [new-pipeline idx]
            (if (pred (get coll idx))
              (reduced idx)
              nil)) nil (range 0 (count coll))))

(defn- inject-row-level-permissions-middleware
  "Looks for `maybe-apply-row-level-permissions` middleware in the main query processor middleware datastructure. If
  not found, will add itself, immediately after `log-query/log-initial-query`. Is a noop if it's already present."
  [query-pipeline-vars]
  (if-let [resolve-index (and (not-any? #(= #'maybe-apply-row-level-permissions %) query-pipeline-vars)
                              (vec-index-of #(= % #'log-query/log-initial-query) query-pipeline-vars))]
    (vec (concat
          (subvec query-pipeline-vars 0 (inc resolve-index))
          [#'maybe-apply-row-level-permissions]
          (subvec query-pipeline-vars (inc resolve-index))))
    query-pipeline-vars))

(defn update-qp-pipeline-for-mt
  "Update the query pipeline atom to include the row level restrictions middleware. Intended to be called on startup."
  []
  (swap! qp/pipeline-functions inject-row-level-permissions-middleware))
