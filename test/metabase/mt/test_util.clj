(ns metabase.mt.test-util
  "Shared test utilities for multi-tenant tests."
  (:require [metabase
             [sync :as sync]
             [util :as u]]
            [metabase.models
             [card :refer [Card]]
             [database :refer [Database]]
             [permissions :as perms]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :refer [Table]]
             [user :refer [User]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.mt.test-util :as mt.tu]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data
             [dataset-definitions :as defs]
             [users :as users]]
            [toucan.util.test :as tt]))

(defn add-segmented-perms!
  "Removes the default full permissions for all users and adds segmented and read permissions"
  [database-or-id]
  (perms/revoke-permissions! (perms-group/all-users) database-or-id)
  (perms/grant-permissions! (perms-group/all-users) (perms/table-read-path (Table (data/id :venues))))
  (perms/grant-permissions! (perms-group/all-users) (perms/table-segmented-query-path (Table (data/id :venues)))))

(defn call-with-segmented-perms
  "This function creates a new database with the test data so that our test users permissions can be safely changed
  without affect other tests that use those same accounts and the test database."
  [f]
  (data/with-db (data/get-or-create-database! defs/test-data)
    ;; copy the test database
    (tt/with-temp Database [{db-id :id :as db} (select-keys (data/db) [:details :engine :name])]
      (users/create-users-if-needed!)
      (sync/sync-database! db)
      (data/with-db db
        (f db-id)))))

(defmacro with-segmented-perms [[db-binding] & body]
  `(call-with-segmented-perms (fn [db-id#]
                                (let [~db-binding (Database db-id#)]
                                  ~@body))))

(defmacro with-user-attributes
  "Execute `body` with the attributes for a temporary attributes for a test user.

    (with-user-attributes :rasta {\"cans\" 2}
      ...)"
  {:style/indent 2}
  [user-kwd attributes-map & body]
  `(tu/with-temp-vals-in-db User (users/user->id ~user-kwd) {:login_attributes ~attributes-map}
     ~@body))

(defn restricted-column-query [db-id]
  {:database db-id
   :type     :query
   :query    (data/$ids venues
               {:source_table $$table
                :fields       [[:field-id $id]
                               [:field-id $name]
                               [:field-id $category_id]]})})

(defn call-with-segmented-test-setup [make-query-fn f]
  (with-segmented-perms [db]
    (let [attr-remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}]
      (tt/with-temp* [Card                       [card  {:name          "magic"
                                                         :dataset_query (make-query-fn (u/get-id db))}]
                      PermissionsGroup           [group {:name "Restricted Venues"}]
                      PermissionsGroupMembership [_     {:group_id (u/get-id group)
                                                         :user_id  (users/user->id :rasta)}]
                      GroupTableAccessPolicy     [gtap  {:group_id             (u/get-id group)
                                                         :table_id             (data/id :venues)
                                                         :card_id              (u/get-id card)
                                                         :attribute_remappings attr-remappings}]]
        (add-segmented-perms! db)
        (f)))))

(defmacro with-segmented-test-setup [make-query-fn & body]
  `(call-with-segmented-test-setup ~make-query-fn (fn [] ~@body)))
