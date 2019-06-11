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
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data
             [dataset-definitions :as defs]
             [users :as users]]
            [schema.core :as s]
            [toucan.util.test :as tt]))

(defn ^:deprecated do-with-copy-of-test-db
  "This function creates a new database with the test data so that our test users permissions can be safely changed
  without affect other tests that use those same accounts and the test database."
  [f]
  (data/with-db (data/get-or-create-database! defs/test-data)
    ;; copy the test database
    (tt/with-temp Database [{db-id :id, :as db} (select-keys (data/db) [:details :engine :name])]
      (users/create-users-if-needed!)
      (sync/sync-database! db)
      (data/with-db db
        (f (Database db-id))))))

(defmacro ^:deprecated with-copy-of-test-db
  "Run `body` with a copy of the usual test database, so we can go crazy changing it without worrying about affecting
  the primary test data DB. DB is bound for use by `data/db` and `data/id`.

  DEPRECATED -- There's a new `with-copy-of-db` macro that does the same thing in Metabase 0.33.0+. Use that instead."
  [[db-binding] & body]
  `(do-with-copy-of-test-db (fn [~db-binding] ~@body)))


(defmacro with-user-attributes
  "Execute `body` with the attributes for a user temporarily set to `attributes-map`.

    (with-user-attributes :rasta {\"cans\" 2}
      ...)"
  {:style/indent 2}
  [user-kwd attributes-map & body]
  `(tu/with-temp-vals-in-db User (users/user->id ~user-kwd) {:login_attributes ~attributes-map}
     ~@body))

(defn do-with-group [group f]
  (tt/with-temp* [PermissionsGroup           [group group]
                  PermissionsGroupMembership [_     {:group_id (u/get-id group)
                                                     :user_id  (users/user->id :rasta)}]]
    (f group)))

(defmacro with-group
  "Create a new PermissionsGroup, bound to `group-binding`; grant test user Rasta Toucan [RIP] permissions for the
  group, then execute `body`."
  [[group-binding group] & body]
  `(do-with-group ~group (fn [~group-binding] ~@body)))


(defn- do-with-gtap-defs
  {:style/indent 2}
  [group, [[table-kw {:keys [query remappings]} :as gtap-def] & more], f]
  (if-not gtap-def
    (f)
    (let [do-with-card (fn [f]
                         (if query
                           (tt/with-temp Card [{card-id :id} {:dataset_query query}]
                             (f card-id))
                           (f nil)))]
      (do-with-card
       (fn [card-id]
         (tt/with-temp GroupTableAccessPolicy [gtap {:group_id             (u/get-id group)
                                                     :table_id             (data/id table-kw)
                                                     :card_id              card-id
                                                     :attribute_remappings remappings}]
           (perms/grant-permissions! group (perms/table-segmented-query-path (Table (data/id table-kw))))
           (do-with-gtap-defs group more f)))))))

(def ^:private WithGTAPsArgs
  "Schema for valid arguments to `with-gtaps`."
  {:gtaps
   {(s/named s/Keyword "Table") (s/maybe
                                 {(s/optional-key :query)      (s/pred map?)
                                  (s/optional-key :remappings) (s/pred map?)})}

   (s/optional-key :attributes)
   (s/pred map?)})

(defn do-with-gtaps [args-fn f]
  (with-copy-of-test-db [db]                                                 ; copy test data DB, bind to `data/db`
    (perms/revoke-permissions! (perms-group/all-users) db)                   ; remove perms for All Users group
    (with-group [group]                                                      ; create new perms group
      (let [{:keys [gtaps attributes]} (s/validate WithGTAPsArgs (args-fn))]
        (with-user-attributes :rasta attributes                              ; set Rasta login_attributes
          (do-with-gtap-defs group gtaps                                     ; create Cards/GTAPs from defs
            (fn []
              (users/with-test-user :rasta                                   ; bind Rasta as current user
                (f group)))))))))                                            ; run (f)

(defmacro with-gtaps
  "Execute `body` with `gtaps` and optionally user `attributes` in effect. All underlying objects and permissions are
  created automatically.

  `gtaps-and-attributes-m` is a map containing `:gtaps` and optionally `:attributes`; see the `WithGTAPsArgs` schema
  in this namespace.

  *  `:gtaps` is a map of test ID table name -> gtap def. Both `:query` and `:remappings` are optional.

  *  If `:query` is specified, a corresponding Card is created, and the GTAP is saved with that `:card_id`.
     Otherwise Card ID is nil and the GTAP uses the source table directly.

  *  `:remappings`, if specified, is saved as the `:attribute_remappings` property of the GTAP.

    (mt.tu/with-gtaps {:gtaps      {:checkins {:query      {:database (data/id), ...}
                                               :remappings {:user_category [\"variable\" ...]}}}
                       :attributes {\"user_category\" 1}}
      (data/run-mbql-query checkins {:limit 2}))"
  {:style/indent 1}
  [gtaps-and-attributes-m & body]
  `(do-with-gtaps (fn [] ~gtaps-and-attributes-m) (fn [~'&group] ~@body)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                            DEPRECATED HELPER MACROS                                            |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn ^:deprecated add-segmented-perms-for-venues-for-all-users-group!
  "Removes the default full permissions for all users and adds segmented and read permissions

  DEPRECATED: Use `with-gtaps` macro instead, and you won't need to do this yourself."
  [database-or-id]
  (perms/revoke-permissions! (perms-group/all-users) database-or-id)
  (perms/grant-permissions! (perms-group/all-users) (perms/table-read-path (Table (data/id :venues))))
  (perms/grant-permissions! (perms-group/all-users) (perms/table-segmented-query-path (Table (data/id :venues)))))

(defn ^:deprecated restricted-column-query [db-id]
  {:database db-id
   :type     :query
   :query    (data/$ids venues
               {:source_table $$table
                :fields       [[:field-id $id]
                               [:field-id $name]
                               [:field-id $category_id]]})})

(defn ^:deprecated call-with-segmented-test-setup [make-query-fn f]
  (with-copy-of-test-db [db]
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
        (add-segmented-perms-for-venues-for-all-users-group! db)
        (f)))))

(defmacro ^:deprecated with-segmented-test-setup
  "Helper for writing segmented permissions tests. Does the following:

  1.  Creates copy of test data DB, binds it for use by `data/db` and `data/id`
  2.  Creates a Card to serve as the GTAP for the `venues` Table. Card uses query created by calling `(make-query-fn db)`
  3.  Creates a new Perms Group, and adds Rasta Toucan [RIP] to it
  4.  Assigns GTAP to new perms group & `venues` Table
  5.  Removes default full permissions for the DB for the 'All Users' Group, so GTAPs are used instead
  6.  Runs `body`

  DEPRECATED: Prefer `with-gtaps` instead, which is clearer and more flexible."
  [make-query-fn & body]
  `(call-with-segmented-test-setup ~make-query-fn (fn [] ~@body)))
