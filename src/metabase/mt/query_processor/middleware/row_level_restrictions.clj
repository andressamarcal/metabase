(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.api.common :as api]
            [metabase.models
             [card :refer [Card]]
             [database :as database]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware.log :as log-query]
            [schema.core :as s]
            [toucan.db :as db]))

(defn- gtap-for-table [query]
  (let [groups (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id (:user query)))]
    (if (seq groups)
      (let [[gtap & more-gtaps] (db/select GroupTableAccessPolicy :group_id [:in groups]
                                           :table_id (get-in query [:query :source-table]))]
        (if (seq more-gtaps)
          (throw (RuntimeException. (format "Found more than one group table access policy for user '%s'" (get-in query [:user :email]))))
          gtap))
      (throw (RuntimeException. (format "User with email '%s' is not a member of any group") (get-in query [:user :email]))))))

(defn- login-attr->template-tag [attribute_remappings [attr-name attr-value]]
  (let [remapped-name (get attribute_remappings attr-name ::not-found)]
    (when-not (= remapped-name ::not-found)
      {:type "category", :value attr-value, :target ["variable" ["template-tag" (name remapped-name)]]})))

(defn- apply-row-level-permissions
  "Does the work of swapping the given table the user was querying against with a nested subquery that restricts the
  rows returned"
  [qp query]
  (if-let [{:keys [card_id attribute_remappings] :as gtap} (gtap-for-table query)]
    (-> query
        (assoc :database database/virtual-id
               :type     :query)
        (update :query assoc :source-table (str "card__" card_id))
        (update :parameters into (keep #(login-attr->template-tag attribute_remappings %) (get-in query [:user :login_attributes])))
        qp)
    (qp query)))

(defn maybe-apply-row-level-permissions
  "Applies row level permissions if the user has segmented permissions. If the user has full permissions, the data
  just passes through with no changes"
  [qp]
  (fn [{{table-id :source-table} :query :as query}]
    (let [table (when (and (seq @api/*current-user-permissions-set*) (integer? table-id))
                  (Table table-id))]
      (cond
        (or
         ;; TODO: This seems like it's wrong, but most of the tests break as they aren't running as a user. I need to
         ;; go back through and either add that to the test suite or do something else here - RS
         (empty? @api/*current-user-permissions-set*)
         ;; If there is no table id, no need to apply row level permissions
         (not table)
         ;; TODO - include permissions in the query context to allow for user aware pulses
         ;; If the user has full permissions, no need to apply row level permissions
         (perms/set-has-full-permissions? @api/*current-user-permissions-set* (perms/table-query-path table)))
        (qp query)

        ;;If user has segmented permissions, apply row-level-permission middleware
        (perms/set-has-full-permissions? @api/*current-user-permissions-set* (perms/table-segmented-query-path table))
        (apply-row-level-permissions qp query)

        :else
        (throw (RuntimeException. (format "User '%s' without read permissions should not be allowed to query table"
                                          (get-in query [:user :email]))))))))

(defn- vec-index-of [pred coll]
  (reduce (fn [new-pipeline idx]
            (if (pred (get coll idx))
              (reduced idx)
              nil)) nil (range 0 (count coll))))

(defn- inject-row-level-permissions-middleware
  "Looks for `maybe-apply-row-level-permissions` middleware in the main query processor middleware datastructure. If
  not found, will add itself. Is a noop if it's already present."
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
