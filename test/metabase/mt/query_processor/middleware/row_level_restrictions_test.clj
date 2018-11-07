(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [clojure.string :as str]
            [expectations :refer [expect]]
            [metabase
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
             [table :refer [Table]]
             [user :refer [User]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.mt.test-util :as mt.tu]
            [metabase.query-processor.util :as qputil]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data
             [datasets :as datasets]
             [generic-sql :as gsql]
             [users :as users]]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                      UTIL                                                      |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- quote-native-identifier
  ([{db-name :name :as db} table-name]
   (gsql/qualify+quote-name datasets/*driver* (name db-name) (name table-name)))
  ([{db-name :name :as db} table-name field-name]
   (gsql/qualify+quote-name datasets/*driver* (name db-name) (name table-name) (name field-name))))

(defn- venues-count-mbql-query []
  {:database (data/id)
   :type     :query
   :query    {:source-table (data/id :venues)
              :aggregation  [[:count]]}})

(defn- venues-source-table-card [db-or-id]
  {:dataset_query {:database (u/get-id db-or-id)
                   :type     :query
                   :query    {:source_table (data/id :venues)}}})

(defn- checkins-source-table-card [db-or-id]
  {:dataset_query {:database (u/get-id db-or-id)
                   :type     :query
                   :query    {:source_table (data/id :checkins)
                              :filter       [">" (data/id :checkins :date) "2014-01-01"]}}})

(defn- do-with-group [group f]
  (tt/with-temp* [PermissionsGroup           [group (merge {:name "Restricted Venues"} group)]
                  PermissionsGroupMembership [_     {:group_id (u/get-id group)
                                                     :user_id  (users/user->id :rasta)}]]
    (f group)))

(defmacro with-group [[group-binding group] & body]
  `(do-with-group ~group (fn [~group-binding] ~@body)))

(defmacro with-gtaps [[group-binding group, & gtap-bindings-and-definitions] & body]
  `(with-group [~group-binding ~group]
     (tt/with-temp* [~@(reduce concat (for [[gtap-binding gtap] (partition 2 gtap-bindings-and-definitions)]
                                        [GroupTableAccessPolicy [gtap-binding gtap]]))]
       ~@body)))

(defn- process-query-with-rasta [query]
  (users/with-test-user :rasta
    (qp/process-query query)))

(defn- gtap {:style/indent 3} [group-or-id table-or-kw-or-id-or-nil card-or-id-or-nil & {:as kvs}]
  (merge {:group_id (u/get-id group-or-id)
          :table_id (if (keyword? table-or-kw-or-id-or-nil)
                      (data/id table-or-kw-or-id-or-nil)
                      (some-> table-or-kw-or-id-or-nil u/get-id))
          :card_id  (some-> card-or-id-or-nil u/get-id)}
         kvs))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                     TESTS                                                      |
;;; +----------------------------------------------------------------------------------------------------------------+

;; When querying with full permissions, no changes should be made
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[100]]
  (tt/with-temp Card [card (venues-source-table-card (data/id))]
    (with-gtaps [group nil
                 _     (gtap group :venues card
                         :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
      (mt.tu/with-user-attributes :rasta {:cat 50}
        (qpt/format-rows-by [int]
          (-> {:database (data/id)
               :type     :query
               :query    {:source-table (data/id :venues)
                          :aggregation  [[:count]]}}
              process-query-with-rasta
              qpt/rows))))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is a native
;; query
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card {:dataset_query {:database (u/get-id db)
                                              :type     :native
                                              :native   {:query         (format "SELECT * FROM %s WHERE %s = {{cat}}"
                                                                                (quote-native-identifier db :venues)
                                                                                (quote-native-identifier db :venues :category_id))
                                                         :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
      (with-gtaps [group nil
                   _     (gtap group :venues card, :attribute_remappings {:cat ["variable" ["template-tag" "cat"]]})]
        (mt.tu/add-segmented-perms! db)
        (mt.tu/with-user-attributes :rasta {"cat" 50}
          (qpt/format-rows-by [int]
            (-> (venues-count-mbql-query)
                process-query-with-rasta
                qpt/rows)))))))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is MBQL
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (venues-source-table-card (u/get-id db))]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
        (mt.tu/add-segmented-perms! db)
        (mt.tu/with-user-attributes :rasta {"cat" 50}
          (qpt/format-rows-by [int]
            (-> (venues-count-mbql-query)
                process-query-with-rasta
                qpt/rows)))))))

;; When processing a query that requires a user attribute and that user attribute isn't there, throw an exception
;; letting the user know it's missing
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  "Query requires user attribute `cat`"
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (venues-source-table-card (u/get-id db))]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
        (mt.tu/add-segmented-perms! db)
        (mt.tu/with-user-attributes :rasta {"something_random" 50}
          (-> (venues-count-mbql-query)
              process-query-with-rasta
              :error))))))

;; Another basic test, same as above, but with a numeric string that needs to be coerced
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (venues-source-table-card (u/get-id db))]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
        (mt.tu/add-segmented-perms! db)
        (qpt/format-rows-by [int]
          (mt.tu/with-user-attributes :rasta {"cat" "50"}
            (-> (venues-count-mbql-query)
                process-query-with-rasta
                qpt/rows)))))))

;; Another basic test, this one uses a stringified float for the login attribute
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[3]]
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (venues-source-table-card (u/get-id db))]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :latitude)]]})]
        (mt.tu/add-segmented-perms! db)
        (qpt/format-rows-by [int]
          (mt.tu/with-user-attributes :rasta {"cat" "34.1018"}
            (-> (venues-count-mbql-query)
                process-query-with-rasta
                qpt/rows)))))))

;; Tests that users can have a different parameter name in their query than they have in their user attributes
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card {:dataset_query {:database (u/get-id db)
                                              :type     :native
                                              :native   {:query         (format "SELECT * FROM %s WHERE %s = {{cat}}"
                                                                                (quote-native-identifier db :venues)
                                                                                (quote-native-identifier db :venues :category_id))
                                                         :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:something.different ["variable" ["template-tag" "cat"]]})]
        (mt.tu/add-segmented-perms! db)
        (mt.tu/with-user-attributes :rasta {"something.different" 50}
          (qpt/format-rows-by [int]
            (-> (venues-count-mbql-query)
                process-query-with-rasta
                qpt/rows)))))))

;; Make sure that you can still use a SQL-based GTAP without needing to have SQL read perms for the Database This test
;; is disabled on Spark as it doesn't appear to support our aliasing syntax and wants an `AS`. Removing spark from
;; this test, written up as https://github.com/metabase/metabase/issues/8524
(datasets/expect-with-engines (disj (qpt/non-timeseries-engines-with-feature :nested-queries) :sparksql)
  [["20th Century Cafe" 1000]]
  (tt/with-temp Database [db (select-keys (data/db) [:details :engine :name])]
    (with-group [group]
      (sync/sync-database! db)
      (data/with-db db
        (tt/with-temp* [Card [card {:dataset_query {:database (u/get-id db)
                                                    :type     :native
                                                    :native   {:query (format (str "SELECT %s AS venue_name,"
                                                                                   " 1000 AS one_thousand "
                                                                                   "FROM %s "
                                                                                   "ORDER BY lower(%s);")
                                                                              (quote-native-identifier db :venues :name)
                                                                              (quote-native-identifier db :venues)
                                                                              (quote-native-identifier db :venues :name))}}}]
                        GroupTableAccessPolicy [_ (gtap group :venues card)]]
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
                 (qpt/format-rows-by [str int]))))))))

;; When no card_id is included in the GTAP, should default to a query against the table, with the GTAP criteria applied
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (with-gtaps [group nil
                 _     (gtap group :venues nil
                         :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
      (mt.tu/add-segmented-perms! db)
      (mt.tu/with-user-attributes :rasta {"cat" 50}
        (qpt/format-rows-by [int]
          (-> (venues-count-mbql-query)
              process-query-with-rasta
              qpt/rows))))))

;; Same test as above but make sure we coerce a numeric string correctly
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-segmented-perms [db]
    (with-gtaps [group {:name "Restricted Venues"}
                 _     (gtap group :venues nil
                         :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
      (mt.tu/add-segmented-perms! db)
      (mt.tu/with-user-attributes :rasta {"cat" "50"}
        (qpt/format-rows-by [int]
          (-> (venues-count-mbql-query)
              process-query-with-rasta
              qpt/rows))))))

;; Users with view access to the related collection should bypass segmented permissions
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  1
  (tt/with-temp* [Database   [db          (select-keys (data/db) [:details :engine :name])]
                  Collection [collection]
                  Card       [card        {:collection_id (u/get-id collection)}]]
    (with-group [group]
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
              count))))))

;; This test isn't covering a row level restrictions feature, but rather checking it it doesn't break querying of a
;; card as a nested query. Part of the row level perms check is looking at the table (or card) to see if row level
;; permissions apply. This was broken when it wasn't expecting a card and only expecting resolved source-tables
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries)
  [[100]]
  (tt/with-temp Card [card (venues-source-table-card (data/id))]
    (->> (process-query-with-rasta
           {:database (data/id)
            :type     :query
            :query    {:source-table (format "card__%s" (u/get-id card))
                       :aggregation  [["count"]]}})
         qpt/rows
         (qpt/format-rows-by [int]))))

(defn- grant-all-segmented! [db-or-id & table-kwds]
  (perms/revoke-permissions! (perms-group/all-users) (u/get-id db-or-id))
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
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (checkins-source-table-card db)]
      (with-gtaps [group nil
                   _     (gtap group :checkins card
                           :attribute_remappings {:user ["variable" [:field-id (data/id :checkins :user_id)]]})]
        (grant-all-segmented! db :checkins)
        (perms/grant-permissions! (perms-group/all-users) (perms/table-query-path (Table (data/id :venues))))
        (perms/grant-permissions! (perms-group/all-users) (perms/table-query-path (Table (data/id :users))))
        (mt.tu/with-user-attributes :rasta {"user" 5}
          (qpt/format-rows-by [int int]
            (-> {:database (data/id)
                 :type     :query
                 :query    {:source-table (data/id :checkins)
                            :aggregation  [[:count]]
                            :order-by     [[:asc [:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]]
                            :breakout     [[:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]}}
                process-query-with-rasta
                qpt/rows)))))))

;; Test that we're able to use a GTAP for an FK related table. For this test, the user has segmented permissions on
;; checkins and venues, so we need to apply a GTAP to the original table (checkins) in addition to the related table
;; (venues). This test uses a GTAP question for both tables
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil 45] [1 10]}
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp* [Card [card-1 {:dataset_query {:database (u/get-id db)
                                                  :type     :query
                                                  :query    {:source_table (data/id :checkins)
                                                             :filter       [">" (data/id :checkins :date) "2014-01-01"]}}}]
                    Card [card-2 (venues-source-table-card (u/get-id db))]]
      (with-gtaps [group nil
                   _     (gtap group :checkins card-1
                           :attribute_remappings {:user ["variable" [:field-id (data/id :checkins :user_id)]]})
                   _     (gtap group :venues card-2
                           :attribute_remappings {:price ["variable" [:field-id (data/id :venues :price)]]})]
        (grant-all-segmented! db :checkins :venues)
        (set
         (mt.tu/with-user-attributes :rasta {"user" 5, "price" 1}
           (qpt/format-rows-by [#(when % (int %)) int]
             (-> {:database (data/id)
                  :type     :query
                  :query    {:source-table (data/id :checkins)
                             :aggregation  [[:count]]
                             :order-by     [[:asc [:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]]
                             :breakout     [[:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]}}
                 process-query-with-rasta
                 qpt/rows))))))))

;; Test that the FK related table can be a "default" GTAP, i.e. a GTAP where the `card_id` is nil
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil 45] [1 10]}
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (checkins-source-table-card db)]
      (with-gtaps [group nil
                   _     (gtap group :checkins card
                           :attribute_remappings {:user ["variable" [:field-id (data/id :checkins :user_id)]]})
                   _     (gtap group :venues nil
                           :attribute_remappings {:price ["variable" [:field-id (data/id :venues :price)]]})]
        (grant-all-segmented! db :checkins :venues)
        (set
         (mt.tu/with-user-attributes :rasta {"user"  5
                                             "price" 1}
           (qpt/format-rows-by [#(when % (int %)) int]
             (-> {:database (data/id)
                  :type     :query
                  :query    {:source-table (data/id :checkins)
                             :aggregation  [[:count]]
                             :order-by     [[:asc [:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]]
                             :breakout     [[:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]}}
                 process-query-with-rasta
                 qpt/rows))))))))

;; Test that we have multiple FK related, segmented tables. This test has checkins with a GTAP question with venues
;; and users having the default GTAP and segmented permissions
(datasets/expect-with-engines (qpt/non-timeseries-engines-with-feature :nested-queries :foreign-keys)
  #{[nil "Quentin Sören" 45] [1 "Quentin Sören" 10]}
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (checkins-source-table-card db)]
      (with-gtaps [group nil
                   _     (gtap group :checkins card
                           :attribute_remappings {:user ["variable" [:field-id (data/id :checkins :user_id)]]})
                   _     (gtap group :venues nil
                           :attribute_remappings {:price ["variable" [:field-id (data/id :venues :price)]]})
                   _     (gtap group :users nil
                           :attribute_remappings {:user ["variable" [:field-id (data/id :users :id)]]})]
        (grant-all-segmented! db :checkins :venues :users)
        (set
         (mt.tu/with-user-attributes :rasta {"user" 5, "price" 1}
           (qpt/format-rows-by [#(when % (int %)) str int]
             (-> {:database (data/id)
                  :type     :query
                  :query    {:source-table (data/id :checkins)
                             :aggregation  [[:count]]
                             :order-by     [[:asc [:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]]]
                             :breakout     [[:fk-> (data/id :checkins :venue_id) (data/id :venues :price)]
                                            [:fk-> (data/id :checkins :user_id) (data/id :users :name)]]}}
                 process-query-with-rasta
                 qpt/rows))))))))

;; make sure GTAP queries still include ID of user who ran them in the remark
(defn- run-query-returning-remark [run-query-fn]
  (let [remark        (atom nil)
        query->remark qputil/query->remark]
    (with-redefs [qputil/query->remark (fn [outer-query]
                                         (u/prog1 (query->remark outer-query)
                                           (reset! remark <>)))]
      (let [results (run-query-fn)]
        (or (some-> @remark (str/replace #"queryHash: \w+" "queryHash: <hash>"))
            (println "NO REMARK FOUND:\n" (u/pprint-to-str 'red results))
            (throw (ex-info "No remark found!" {:results results})))))))

(expect
  (format "Metabase:: userID: %d queryType: MBQL queryHash: <hash>" (users/user->id :rasta))
  (mt.tu/with-segmented-perms [db]
    (tt/with-temp Card [card (venues-source-table-card (u/get-id db))]
      (with-gtaps [group nil
                   _     (gtap group :venues card
                           :attribute_remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]})]
        (mt.tu/add-segmented-perms! db)
        (tu/with-temp-vals-in-db User (users/user->id :rasta) {:login_attributes {"cat" 50}}
          (run-query-returning-remark
           (fn []
             ((users/user->client :rasta) :post "dataset" (venues-count-mbql-query)))))))))
