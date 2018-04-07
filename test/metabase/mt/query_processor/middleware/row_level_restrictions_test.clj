(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [clojure.string :as str]
            [expectations :refer :all]
            [metabase
             [middleware :as mid]
             [query-processor :as qp]
             [query-processor-test :as qpt]
             [sync :as sync]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.models
             [card :refer [Card]]
             [database :refer [Database]]
             [field :refer [Field]]
             [permissions :refer [Permissions] :as perms]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware.expand :as ql]
            [metabase.test.data :as data]
            [metabase.test.data
             [dataset-definitions :as defs]
             [users :as users]]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

(defn- with-user-attributes [query-context user-attributes]
  (assoc query-context :user (assoc (#'mid/find-user (users/user->id :rasta))
                               :login_attributes user-attributes)))

(defn- table [database-or-id table-kw & {:keys [fields]}]
  (or (db/select-one (if (seq fields)
                       (vec (cons Table fields))
                       Table)
        :db_id       (u/get-id database-or-id)
        :%lower.name (str/lower-case (name table-kw)))
      (throw (Exception. (format "Table '%s' not found, found: %s"
                                 (name table-kw)
                                 (db/select-field :name Table :db_id (u/get-id database-or-id)))))))

(defn- field [database-or-id table-kw field-kw & {:keys [fields]}]
  (let [table-id (u/get-id (table database-or-id table-kw, :fields [:id]))]
    (or (db/select-one (if (seq fields)
                         (vec (cons Field fields))
                         Field)
          :table_id    table-id
          :%lower.name (str/lower-case (name field-kw)))
        (throw (Exception. (format "Field '%s' not found, found: %s"
                                   (name field-kw)
                                   (db/select-field :name Field :table_id table-id)))))))

(defn- id'
  "Similar to `metabase.test.data/id`, but looks for the table/field id associated with `db-id` "
  ([database-or-id table-kw]          (u/get-id (table database-or-id table-kw,          :fields [:id])))
  ([database-or-id table-kw field-kw] (u/get-id (field database-or-id table-kw field-kw, :fields [:id]))))


(defn- call-with-segmented-perms
  [f]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp Database [{db-id :id :as db} {:details (:details (data/db)), :engine "h2"}]
      (users/create-users-if-needed!)
      (metabase.sync/sync-database! db)
      (f db-id))))

(defn- add-segmented-perms
  "Removes the default full permissions for all users and adds segmented and read permissions"
  [db-id]
  (perms/revoke-permissions! (perms-group/all-users) db-id)
  (perms/grant-permissions! (perms-group/all-users) (perms/table-read-path (Table (id' db-id :venues))))
  (perms/grant-permissions! (perms-group/all-users) (perms/table-segmented-query-path (Table (id' db-id :venues)))))

;; TODO remove the need for native read permissions on segmented queries
(defn- add-native-read-perms
  "Queries against a table that have a native GTAP question currently require native read permissions, invoking this
  will set that up for `db-id` for all users"
  [db-id]
  (perms/grant-permissions! (perms-group/all-users) (perms/native-read-path db-id)))

;; When querying with full permissions, no changes should be made
(expect
  [[100]]
  (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                :dataset_query {:database (data/id)
                                                                :type     :query
                                                                :query    {:source_table (data/id :venues)
                                                                           :filter ["=" ["field-id" (data/id :venues :category_id)]
                                                                                    ["param-value" (data/id :venues :category_id) "cat"]]}}}]
                  PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                  PermissionsGroupMembership [_ {:group_id group-id
                                                 :user_id  (users/user->id :rasta)}]
                  GroupTableAccessPolicy [gtap {:group_id group-id
                                                :table_id (data/id :venues)
                                                :card_id card-id
                                                :attribute_remappings {:cat :cat}}]]

    (users/call-with-api-vars
     :rasta
     (fn []
       (-> (data/query :venues
                       (ql/aggregation (ql/count)))
           data/wrap-inner-query
           (with-user-attributes {:cat 50})
           qp/process-query
           qpt/rows)))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is a native
;; query
(expect
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                   :dataset_query {:database db-id
                                                                   :type     :native
                                                                   :native   {:query "SELECT * FROM VENUES WHERE category_id = {{cat}}"
                                                                              :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (id' db-id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:cat :cat}}]]

       (add-segmented-perms db-id)
       (add-native-read-perms db-id)

       (users/call-with-api-vars
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (id' db-id :venues))
                        (ql/aggregation (ql/count)))
              data/wrap-inner-query
              (with-user-attributes {"cat" 50})
              qp/process-query
              qpt/rows)))))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is MBQL
(expect
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                   :dataset_query {:database db-id
                                                                   :type     :query
                                                                   :query    {:source_table (id' db-id :venues)
                                                                              :filter ["=" ["field-id" (id' db-id :venues :category_id)]
                                                                                       ["param-value" (id' db-id :venues :category_id) "cat"]]}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (id' db-id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:cat :cat}}]]
       (add-segmented-perms db-id)

       (users/call-with-api-vars
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (id' db-id :venues))
                        (ql/aggregation (ql/count)))
              data/wrap-inner-query
              (with-user-attributes {"cat" 50})
              qp/process-query
              qpt/rows)))))))

;; Tests that users can user a different parameter name in their query than they have in their user attributes
(expect
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                   :dataset_query {:database db-id
                                                                   :type     :query
                                                                   :query    {:source_table (id' db-id :venues)
                                                                              :filter ["=" ["field-id" (id' db-id :venues :category_id)]
                                                                                       ["param-value" (id' db-id :venues :category_id) "cat"]]}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (id' db-id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:something.different :cat}}]]

       (add-segmented-perms db-id)

       (users/call-with-api-vars
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (id' db-id :venues))
                        (ql/aggregation (ql/count)))
              data/wrap-inner-query
              (with-user-attributes {"something.different" 50})
              qp/process-query
              qpt/rows)))))))

;; Make sure that you can still use a SQL-based GTAP without needing to have SQL read perms for the Database
(expect
 [["20th Century Cafe" 1000]]
 (tt/with-temp* [Database                   [db (select-keys (data/db) [:details :engine])]
                 PermissionsGroup           [group {:name "Segmented Access Group :/"}]
                 PermissionsGroupMembership [_ {:group_id (u/get-id group), :user_id (users/user->id :rasta)}]]
   (sync/sync-database! db)
   (tt/with-temp* [Card [gtap-card {:dataset_query {:database (u/get-id db)
                                                    :type     :native
                                                    :native   {:query (str "SELECT name AS \"venue_name\","
                                                                           " 1000 AS \"one_thousand\" "
                                                                           "FROM venues "
                                                                           "ORDER BY lower(name);")}}}]
                   GroupTableAccessPolicy [_ {:group_id (u/get-id group)
                                              :table_id (id' db :venues)
                                              :card_id  (u/get-id gtap-card)}]]
     (perms/revoke-permissions! (perms-group/all-users) (u/get-id db))
     (perms/grant-permissions! group (perms/table-segmented-query-path (table db :venues)))
     (binding [*current-user-id*              (users/user->id :rasta)
               *current-user-permissions-set* (let [perms (db/select-field :object Permissions :group_id (u/get-id group))]
                                                (atom perms))]
       (-> (qp/process-query {:database (u/get-id db)
                              :type     :query
                              :query    {:source-table (id' db :venues)
                                         :limit 1}
                              :user     (users/fetch-user :rasta)})
           qpt/rows)))))
