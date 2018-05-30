(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.models
             [database :as database]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware
             [log :as log-query]
             [permissions :as perms-middleware]]
            [metabase.query-processor.util :as qputil]
            [toucan.db :as db])
  (:import metabase.query_processor.middleware.permissions.TablesPermsCheck))

(defn- gtap-for-table [query]
  (let [groups (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id (:user query)))]
    (if (seq groups)
      (let [[gtap & more-gtaps] (db/select GroupTableAccessPolicy :group_id [:in groups]
                                           :table_id (qputil/get-in-normalized query [:query :source-table]))]
        (if (seq more-gtaps)
          (throw (RuntimeException. (format "Found more than one group table access policy for user '%s'" (get-in query [:user :email]))))
          gtap))
      (throw (RuntimeException. (format "User with email '%s' is not a member of any group") (get-in query [:user :email]))))))

(defn- attr-remapping->parameter [login-attributes [attr-name target]]
  ;; defaults attr-value to "" because if it's nil the parameter is ignored
  ;; TODO: maybe we should just throw an exception
  (let [attr-value (get login-attributes attr-name "")]
    {:type "category", :value attr-value, :target target}))

(defn- apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned"
  [qp query]
  (if-let [{:keys [card_id attribute_remappings] :as gtap} (gtap-for-table query)]
    (let [login-attributes (qputil/get-in-normalized query [:user :login-attributes])]
      (-> query
          (assoc :database database/virtual-id
                 :type     :query)
          ;; We need to dissoc the source-table before associng a new one. Due to normalization, it's possible that
          ;; we'll have `:source_table` and `:source-table` after this next line, removing it ensures we'll at least not
          ;; have more source table entries after the next line
          (update :query qputil/dissoc-normalized :source-table)
          (update :query assoc :source-table (str "card__" card_id))
          (assoc :source-table-is-gtap? true)
          (update :parameters into (map #(attr-remapping->parameter login-attributes %)
                                         attribute_remappings))
          qp))
    (qp query)))


(defn- query-should-have-segmented-permissions?
  "Determine whether we should apply segmented permissions for `query`.

  This function piggybacks off of the logic for determining which permissions checks should take place in the
  permissions-check middleware; if a `TablePermsCheck` is slated to take place, we'll look and see whether the current
  user has either full perms for that Table, or merely segmented perms. This is currently the only type of perms check
  that can be subject to segmentation; we can ignore the other types. Refer to the perms-check namespace for a more
  detailed discussion of the different types of perms checks.

    (query-should-have-segmented-permissions? query) ; -> true"
  [query]
  (boolean
   (when *current-user-id*
     ;; the perms-check middleware works on outer queries. Since we only have the inner query at this point (why?
     (when-let [perms-check (perms-middleware/query->perms-check query)]
       (when (instance? TablesPermsCheck perms-check)
         (let [{:keys [source-table-id]} perms-check
               table                     (db/select-one ['Table :id :db_id :schema] :id source-table-id)]
           (cond
             (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-query-path table))
             false

             (perms/set-has-full-permissions? @*current-user-permissions-set* (perms/table-segmented-query-path table))
             true

             :else
             (throw (Exception. "Invalid state: user does not have either full or segmented query permissions!")))))))))

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
