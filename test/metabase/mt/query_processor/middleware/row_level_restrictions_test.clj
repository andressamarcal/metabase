(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [expectations :refer :all]
            [metabase
             [middleware :as mid]
             [query-processor :as qp]
             [query-processor-test :as qpt]]
            [metabase.models
             [card :refer [Card]]
             [database :refer [Database]]
             [field :refer [Field]]
             [permissions :as perms]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]]
            metabase.mt.query-processor.middleware.row-level-restrictions
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

(defn- id'
  "Similar to `metabase.test.data/id`, but looks for the table/field id associated with `db-id` "
  ([db-id table-kwd]
   (db/select-one-id metabase.models.table/Table :db_id db-id :name (data/format-name table-kwd)))
  ([db-id table-kwd field-kwd]
   (let [table-id (id' db-id table-kwd)]
     (db/select-one-id Field, :active true, :table_id table-id, :name (data/format-name field-kwd)))))

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
                  PermissionsGroupMembership [{perm-group-id :id} {:group_id group-id
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
                     PermissionsGroupMembership [{perm-group-id :id} {:group_id group-id
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
              (with-user-attributes {:cat 50})
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
                     PermissionsGroupMembership [{perm-group-id :id} {:group_id group-id
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
              (with-user-attributes {:cat 50})
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
                     PermissionsGroupMembership [{perm-group-id :id} {:group_id group-id
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
              (with-user-attributes {:something.different 50})
              qp/process-query
              qpt/rows)))))))
