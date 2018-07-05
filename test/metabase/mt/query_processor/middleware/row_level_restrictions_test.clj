(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [expectations :refer :all]
            [metabase
             [middleware :as mid]
             [query-processor :as qp]
             [query-processor-test :as qpt]
             [sync :as sync]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.models
             [card :refer [Card]]
             [collection :refer [Collection]]
             [database :refer [Database]]
             [permissions :as perms :refer [Permissions]]
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

(defn call-with-segmented-perms
  "This function creates a new database with the test data so that our test users permissions can be safely changed
  without affect other tests that use those same accounts and the test database."
  [f]
  (data/with-db (data/get-or-create-database! defs/test-data)
    ;; copy the test database
    (tt/with-temp Database [{db-id :id :as db} {:details (:details (data/db)), :engine "h2"}]
      (users/create-users-if-needed!)
      (metabase.sync/sync-database! db)
      (data/with-db db
        (f db-id)))))

(defn add-segmented-perms
  "Removes the default full permissions for all users and adds segmented and read permissions"
  [db-id]
  (perms/revoke-permissions! (perms-group/all-users) db-id)
  (perms/grant-permissions! (perms-group/all-users) (perms/table-read-path (Table (data/id :venues))))
  (perms/grant-permissions! (perms-group/all-users) (perms/table-segmented-query-path (Table (data/id :venues)))))

;; When querying with full permissions, no changes should be made
(expect
  [[100]]
  (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                :dataset_query {:database (data/id)
                                                                :type     :query
                                                                :query    {:source_table (data/id :venues)}}}]
                  PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                  PermissionsGroupMembership [_ {:group_id group-id
                                                 :user_id  (users/user->id :rasta)}]
                  GroupTableAccessPolicy [gtap {:group_id group-id
                                                :table_id (data/id :venues)
                                                :card_id card-id
                                                :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}}]]

    (users/do-with-test-user
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
                                                   :table_id (data/id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:cat ["variable" ["template-tag" "cat"]]}}]]

       (add-segmented-perms db-id)
       (users/do-with-test-user
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (data/id :venues))
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
                                                                   :query    {:source_table (data/id :venues)}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}}]]
       (add-segmented-perms db-id)
       (users/do-with-test-user
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (data/id :venues))
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
                                                                   :type     :native
                                                                   :native   {:query "SELECT * FROM VENUES WHERE category_id = {{cat}}"
                                                                              :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:something.different ["variable" ["template-tag" "cat"]]}}]]

       (add-segmented-perms db-id)
       (users/do-with-test-user
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (data/id :venues))
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
   (data/with-db db
     (tt/with-temp* [Card [gtap-card {:dataset_query {:database (u/get-id db)
                                                      :type     :native
                                                      :native   {:query (str "SELECT name AS \"venue_name\","
                                                                             " 1000 AS \"one_thousand\" "
                                                                             "FROM venues "
                                                                             "ORDER BY lower(name);")}}}]
                     GroupTableAccessPolicy [_ {:group_id (u/get-id group)
                                                :table_id (data/id :venues)
                                                :card_id  (u/get-id gtap-card)}]]
       (perms/revoke-permissions! (perms-group/all-users) (u/get-id db))
       (perms/grant-permissions! group (perms/table-segmented-query-path (Table (data/id :venues))))
       (binding [*current-user-id*              (users/user->id :rasta)
                 *current-user-permissions-set* (let [perms (db/select-field :object Permissions :group_id (u/get-id group))]
                                                  (atom perms))]
         (-> (qp/process-query {:database (u/get-id db)
                                :type     :query
                                :query    {:source-table (data/id :venues)
                                           :limit        1}
                                :user     (users/fetch-user :rasta)})
             qpt/rows))))))

;; When no card_id is included in the GTAP, should default to a query against the table, with the GTAP criteria applied
(expect
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id             group-id
                                                   :table_id             (data/id :venues)
                                                   :card_id              nil
                                                   :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}}]]
       (add-segmented-perms db-id)
       (users/do-with-test-user
        :rasta
        (fn []
          (-> (ql/query (ql/source-table (data/id :venues))
                        (ql/aggregation (ql/count)))
              data/wrap-inner-query
              (with-user-attributes {"cat" 50})
              qp/process-query
              qpt/rows)))))))

;; Users with view access to the related collection should bypass segmented permissions
(expect
  1
  (tt/with-temp* [Database                   [db (select-keys (data/db) [:details :engine])]
                  Collection                 [collection]
                  Card                       [card {:collection_id (u/get-id collection)}]
                  PermissionsGroup           [group]
                  PermissionsGroupMembership [_ {:user_id (users/user->id :rasta), :group_id (u/get-id group)}]]
    (sync/sync-database! db)
    (data/with-db db
      (perms/revoke-permissions! (perms-group/all-users) (u/get-id db))
      (perms/grant-collection-read-permissions! group collection)
      (binding [*current-user-id*              (users/user->id :rasta)
                *current-user-permissions-set* (let [perms (db/select-field :object Permissions :group_id (u/get-id group))]
                                                 (atom perms))]
        (-> (qp/process-query {:database (u/get-id db)
                               :type     :query
                               :query    {:source-table (data/id :venues)
                                          :limit        1}
                               :user     (users/fetch-user :rasta)
                               :info     {:card-id    (u/get-id card)
                                          :query-hash (byte-array 0)}})
            qpt/rows
            count)))))
