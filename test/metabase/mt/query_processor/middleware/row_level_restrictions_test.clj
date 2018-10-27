(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [expectations :refer :all]
            [metabase
             [middleware :as mid]
             [query-processor :as qp]
             [query-processor-test :as qpt]
             [sync :as sync]
             [util :as u]]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.driver :as driver]
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
            [metabase.query-processor-test :as qpt]
            [metabase.test.data :as data]
            [metabase.test.data
             [dataset-definitions :as defs]
             [datasets :as datasets]
             [generic-sql :as gsql]
             [users :as users]]
            [toucan.db :as db]
            [toucan.util.test :as tt]
            [metabase.api.dataset :as dataset]
            [honeysql.core :as hsql]))

(defn- with-user-attributes [query-context user-attributes]
  (assoc query-context :user (assoc (#'mid/find-user (users/user->id :rasta))
                               :login_attributes user-attributes)))

(defn call-with-segmented-perms
  "This function creates a new database with the test data so that our test users permissions can be safely changed
  without affect other tests that use those same accounts and the test database."
  [f]
  (data/with-db (data/get-or-create-database! defs/test-data)
    ;; copy the test database
    (tt/with-temp Database [{db-id :id :as db} (select-keys (data/db) [:details :engine :name])]
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
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
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
       (qpt/format-rows-by [int]
         (-> (data/query :venues
               (ql/aggregation (ql/count)))
             data/wrap-inner-query
             (with-user-attributes {:cat 50})
             qp/process-query
             qpt/rows))))))

(defn- quote-native-identifier
  ([{db-name :name :as db} table-name]
   (gsql/qualify+quote-name datasets/*driver* (name db-name) (name table-name)))
  ([{db-name :name :as db} table-name field-name]
   (gsql/qualify+quote-name datasets/*driver* (name db-name) (name table-name) (name field-name))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is a native
;; query
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (let [db (Database db-id)]
       (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                     :dataset_query {:database db-id
                                                                     :type     :native
                                                                     :native   {:query (format "SELECT * FROM %s WHERE %s = {{cat}}"
                                                                                               (quote-native-identifier db :venues)
                                                                                               (quote-native-identifier db :venues :category_id))
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
            (qpt/format-rows-by [int]
              (-> (ql/query (ql/source-table (data/id :venues))
                            (ql/aggregation (ql/count)))
                  data/wrap-inner-query
                  (with-user-attributes {"cat" 50})
                  qp/process-query
                  qpt/rows)))))))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is MBQL
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
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
          (qpt/format-rows-by [int]
            (-> (ql/query (ql/source-table (data/id :venues))
                          (ql/aggregation (ql/count)))
                data/wrap-inner-query
                (with-user-attributes {"cat" 50})
                qp/process-query
                qpt/rows))))))))

;; When processing a query that requires a user attribute and that user attribute isn't there, throw an exception
;; letting the user know it's missing
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  "Query requires user attribute `cat`"
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
              (with-user-attributes {"something_random" 50})
              qp/process-query
              :error)))))))

;; Another basic test, same as above, but with a numeric string that needs to be coerced
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
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
          (qpt/format-rows-by [int]
            (-> (ql/query (ql/source-table (data/id :venues))
                          (ql/aggregation (ql/count)))
                data/wrap-inner-query
                (with-user-attributes {"cat" "50"})
                qp/process-query
                qpt/rows))))))))

;; Another basic test, this one uses a stringified float for the login attribute
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[3]]
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
                                                   :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :latitude)]]}}]]
       (add-segmented-perms db-id)
       (users/do-with-test-user
        :rasta
        (fn []
          (qpt/format-rows-by [int]
            (-> (ql/query (ql/source-table (data/id :venues))
                          (ql/aggregation (ql/count)))
                data/wrap-inner-query
                (with-user-attributes {"cat" "34.1018"})
                qp/process-query
                qpt/rows))))))))

;; Tests that users can have a different parameter name in their query than they have in their user attributes
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (call-with-segmented-perms
   (fn [db-id]
     (let [db (Database db-id)]
       (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                     :dataset_query {:database db-id
                                                                     :type     :native
                                                                     :native   {:query (format "SELECT * FROM %s WHERE %s = {{cat}}"
                                                                                               (quote-native-identifier db :venues)
                                                                                               (quote-native-identifier db :venues :category_id))
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
            (qpt/format-rows-by [int]
              (-> (ql/query (ql/source-table (data/id :venues))
                            (ql/aggregation (ql/count)))
                  data/wrap-inner-query
                  (with-user-attributes {"something.different" 50})
                  qp/process-query
                  qpt/rows)))))))))

;; Make sure that you can still use a SQL-based GTAP without needing to have SQL read perms for the Database This test
;; is disabled on Spark as it doesn't appear to support our aliasing syntax and wants an `AS`. Removing spark from
;; this test, written up as https://github.com/metabase/metabase/issues/8524
(datasets/expect-with-engines (disj (qpt/non-timeseries-engines-with-feature :nested-queries) :sparksql)
  [["20th Century Cafe" 1000]]
  (tt/with-temp* [Database                   [db (select-keys (data/db) [:details :engine :name])]
                  PermissionsGroup           [group {:name "Segmented Access Group :/"}]
                  PermissionsGroupMembership [_ {:group_id (u/get-id group), :user_id (users/user->id :rasta)}]]
    (sync/sync-database! db)
    (data/with-db db
      (tt/with-temp* [Card [gtap-card {:dataset_query {:database (u/get-id db)
                                                       :type     :native
                                                       :native   {:query (format (str "SELECT %s AS venue_name,"
                                                                                      " 1000 AS one_thousand "
                                                                                      "FROM %s "
                                                                                      "ORDER BY lower(%s);")
                                                                                 (quote-native-identifier db :venues :name)
                                                                                 (quote-native-identifier db :venues)
                                                                                 (quote-native-identifier db :venues :name))}}}]
                      GroupTableAccessPolicy [_ {:group_id (u/get-id group)
                                                 :table_id (data/id :venues)
                                                 :card_id  (u/get-id gtap-card)}]]
        (perms/revoke-permissions! (perms-group/all-users) (u/get-id db))
        (perms/grant-permissions! group (perms/table-segmented-query-path (Table (data/id :venues))))
        (binding [*current-user-id*              (users/user->id :rasta)
                  *current-user-permissions-set* (let [perms (db/select-field :object Permissions :group_id (u/get-id group))]
                                                   (atom perms))]

          (->> (qp/process-query {:database (u/get-id db)
                                  :type     :query
                                  :query    {:source-table (data/id :venues)
                                             :limit        1}
                                  :user     (users/fetch-user :rasta)})
               qpt/rows
               (qpt/format-rows-by [str int])))))))

;; When no card_id is included in the GTAP, should default to a query against the table, with the GTAP criteria applied
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
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
          (qpt/format-rows-by [int]
            (-> (ql/query (ql/source-table (data/id :venues))
                          (ql/aggregation (ql/count)))
                data/wrap-inner-query
                (with-user-attributes {"cat" 50})
                qp/process-query
                qpt/rows))))))))

;; Same test as above but make sure we coerce a numeric string correctly
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
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
          (qpt/format-rows-by [int]
            (-> (ql/query (ql/source-table (data/id :venues))
                          (ql/aggregation (ql/count)))
                data/wrap-inner-query
                (with-user-attributes {"cat" "50"})
                qp/process-query
                qpt/rows))))))))

;; Users with view access to the related collection should bypass segmented permissions
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  1
  (tt/with-temp* [Database                   [db (select-keys (data/db) [:details :engine :name])]
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

;; This test isn't covering a row level restrictions feature, but rather checking it it doesn't break querying of a
;; card as a nested query. Part of the row level perms check is looking at the table (or card) to see if row level
;; permissions apply. This was broken when it wasn't expecting a card and only expecting resolved source-tables
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[100]]
  (tt/with-temp* [Card [{card-id :id :as card} {:name          "test card"
                                                :dataset_query {:database (data/id)
                                                                :type     :query
                                                                :query    {:source_table (data/id :venues)}}}]]
    (users/do-with-test-user
     :rasta
     (fn []
       (->> (qp/process-query
              {:database (data/id)
               :type :query
               :query {:source-table (format "card__%s" card-id)
                       :aggregation [["count"]]}})
            qpt/rows
            (qpt/format-rows-by [int]))))))

(defn- grant-all-segmented! [db-id & table-kwds]
  (perms/revoke-permissions! (perms-group/all-users) db-id)
  (doseq [table-kwd table-kwds]
    (perms/grant-permissions! (perms-group/all-users) (perms/table-segmented-query-path (Table (data/id table-kwd))))))

;; Test that we can follow FKs to related tables and breakout by columns on those related tables. This test has
;; several things wrapped up which are detailed below
;;
;; 1 - Creates a GTAP filtering question, looking for any checkins happening on or after 2014
;; 2 - Apply the `user` attribute, looking for only our user (i.e. `user_id` =  5)
;; 3 - Checkins are related to Venues, query for checkins, grouping by the Venue's price
;; 4 - Order by the Venue's price to ensure a predictably ordered response
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  [[1 10] [2 36] [3 4] [4 5]]
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id :id} {:name          "magic"
                                          :dataset_query {:database db-id
                                                          :type     :query
                                                          :query    {:source_table (data/id :checkins)
                                                                     :filter [">" (data/id :checkins :date) "2014-01-01"]}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :checkins)
                                                   :card_id card-id
                                                   :attribute_remappings {:user ["variable"
                                                                                 [:field-id (data/id :checkins :user_id)]]}}]]

       (grant-all-segmented! db-id :checkins)
       (perms/grant-permissions! (perms-group/all-users) (perms/table-query-path (Table (data/id :venues))))
       (perms/grant-permissions! (perms-group/all-users) (perms/table-query-path (Table (data/id :users))))
       (users/do-with-test-user
        :rasta
        (fn []
          (qpt/format-rows-by [int int]
            (-> (ql/query (ql/source-table (data/id :checkins))
                          (ql/aggregation (ql/count))
                          (ql/order-by (ql/asc (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                          (ql/breakout (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                data/wrap-inner-query
                (with-user-attributes {"user" 5})
                qp/process-query
                qpt/rows))))))))

;; Test that we're able to use a GTAP for an FK related table. For this test, the user has segmented permissions on
;; checkins and venues, so we need to apply a GTAP to the original table (checkins) in addition to the related table
;; (venues). This test uses a GTAP question for both tables
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil 45] [1 10]}
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id-1 :id} {:name          "magic"
                                            :dataset_query {:database db-id
                                                            :type     :query
                                                            :query    {:source_table (data/id :checkins)
                                                                       :filter [">" (data/id :checkins :date) "2014-01-01"]}}}]
                     Card [{card-id-2 :id} {:name          "magic"
                                            :dataset_query {:database db-id
                                                            :type     :query
                                                            :query    {:source_table (data/id :venues)}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :checkins)
                                                   :card_id card-id-1
                                                   :attribute_remappings {:user ["variable"
                                                                                 [:field-id (data/id :checkins :user_id)]]}}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :card_id card-id-2
                                                   :attribute_remappings {:price ["variable"
                                                                                  [:field-id (data/id :venues :price)]]}}]]
       (grant-all-segmented! db-id :checkins :venues)
       (users/do-with-test-user
        :rasta
        (fn []
          (set
           (qpt/format-rows-by [#(when % (int %)) int]
             (-> (ql/query (ql/source-table (data/id :checkins))
                           (ql/aggregation (ql/count))
                           (ql/order-by (ql/asc (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                           (ql/breakout (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                 data/wrap-inner-query
                 (with-user-attributes {"user" 5
                                        "price" 1})
                 qp/process-query
                 qpt/rows)))))))))

;; Test that the FK related table can be a "default" GTAP, i.e. a GTAP where the `card_id` is nil
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil 45] [1 10]}
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id-1 :id} {:name          "magic"
                                            :dataset_query {:database db-id
                                                            :type     :query
                                                            :query    {:source_table (data/id :checkins)
                                                                       :filter [">" (data/id :checkins :date) "2014-01-01"]}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :checkins)
                                                   :card_id card-id-1
                                                   :attribute_remappings {:user ["variable"
                                                                                 [:field-id (data/id :checkins :user_id)]]}}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :attribute_remappings {:price ["variable"
                                                                                  [:field-id (data/id :venues :price)]]}}]]
       (grant-all-segmented! db-id :checkins :venues)
       (users/do-with-test-user
        :rasta
        (fn []
          (set
           (qpt/format-rows-by [#(when % (int %)) int]
             (-> (ql/query (ql/source-table (data/id :checkins))
                           (ql/aggregation (ql/count))
                           (ql/order-by (ql/asc (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                           (ql/breakout (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                 data/wrap-inner-query
                 (with-user-attributes {"user" 5
                                        "price" 1})
                 qp/process-query
                 qpt/rows)))))))))

;; Test that we have multiple FK related, segmented tables. This test has checkins with a GTAP question with venues
;; and users having the default GTAP and segmented permissions
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil "Quentin Sören" 45] [1 "Quentin Sören" 10]}
  (call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id-1 :id} {:name          "magic"
                                            :dataset_query {:database db-id
                                                            :type     :query
                                                            :query    {:source_table (data/id :checkins)
                                                                       :filter [">" (data/id :checkins :date) "2014-01-01"]}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (users/user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :checkins)
                                                   :card_id card-id-1
                                                   :attribute_remappings {:user ["variable"
                                                                                 [:field-id (data/id :checkins :user_id)]]}}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :attribute_remappings {:price ["variable"
                                                                                  [:field-id (data/id :venues :price)]]}}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :users)
                                                   :attribute_remappings {:user ["variable"
                                                                                 [:field-id (data/id :users :id)]]}}]]
       (grant-all-segmented! db-id :checkins :venues :users)
       (users/do-with-test-user
        :rasta
        (fn []
          (set
           (qpt/format-rows-by [#(when % (int %)) str int]
             (-> (ql/query (ql/source-table (data/id :checkins))
                           (ql/aggregation (ql/count))
                           (ql/order-by (ql/asc (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))))
                           (ql/breakout (ql/fk-> (data/id :checkins :venue_id) (data/id :venues :price))
                                        (ql/fk-> (data/id :checkins :user_id) (data/id :users :name))))
                 data/wrap-inner-query
                 (with-user-attributes {"user" 5
                                        "price" 1})
                 qp/process-query
                 qpt/rows)))))))))
