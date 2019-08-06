(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [clojure.string :as str]
            [expectations :refer [expect]]
            [honeysql.core :as hsql]
            [metabase
             [driver :as driver]
             [query-processor :as qp]
             [query-processor-test :as qp.test]
             [util :as u]]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.mbql
             [normalize :as normalize]
             [util :as mbql.u]]
            [metabase.models
             [card :refer [Card]]
             [collection :refer [Collection]]
             [field :refer [Field]]
             [permissions :as perms]
             [permissions-group :as perms-group]
             [table :refer [Table]]]
            [metabase.mt.query-processor.middleware.row-level-restrictions :as row-level-restrictions]
            [metabase.mt.test-util :as mt.tu]
            [metabase.query-processor
             [interface :as qp.i]
             [test-util :as qp.tu]
             [util :as qputil]]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data
             [datasets :as datasets]
             [env :as tx.env]
             [users :as users]]
            [metabase.util.honeysql-extensions :as hx]
            [toucan.util.test :as tt]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                      SHARED GTAP DEFINITIONS & HELPER FNS                                      |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- identifier
  ([table-key]
   (qp.tu/with-everything-store
     (sql.qp/->honeysql driver/*driver* (Table (data/id table-key)))))

  ([table-key field-key]
   (qp.tu/with-everything-store
     (sql.qp/->honeysql driver/*driver* (Field (data/id table-key field-key))))))


(defn- venues-category-mbql-gtap-def []
  {:query      (data/mbql-query venues)
   :remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}})

(defn- venues-price-mbql-gtap-def []
  {:query      (data/mbql-query venues)
   :remappings {:price ["variable" [:field-id (data/id :venues :price)]]}})

(defn- checkins-user-mbql-gtap-def []
  {:query      (data/mbql-query checkins {:filter [:> $date "2014-01-01"]})
   :remappings {:user ["variable" [:field-id (data/id :checkins :user_id)]]}})

(defn- format-honeysql [honeysql]
  (let [honeysql (cond-> honeysql
                   (= driver/*driver* :sqlserver)
                   (assoc :modifiers ["TOP 1000"])

                   ;; SparkSQL has to have an alias source table (or at least our driver is written as if it has to
                   ;; have one.) HACK
                   (= driver/*driver* :sparksql)
                   (update :from (fn [[table]]
                                   [[table (sql.qp/->honeysql :sparksql
                                             (hx/identifier :table-alias @(resolve 'metabase.driver.sparksql/source-table-alias)))]])))]
    (first (hsql/format honeysql, :quoting (sql.qp/quote-style driver/*driver*), :allow-dashed-names? true))))

(defn- venues-category-native-gtap-def []
  (assert (driver/supports? driver/*driver* :native-parameters))
  {:query (data/native-query
            {:query
             (format-honeysql
              {:select   [:*]
               :from     [(identifier :venues)]
               :where    [:= (identifier :venues :category_id) (hsql/raw "{{cat}}")]
               :order-by [(identifier :venues :id)]})

             :template_tags
             {:cat {:name "cat" :display_name "cat" :type "number" :required true}}})
   :remappings {:cat ["variable" ["template-tag" "cat"]]}})

(defn- parameterized-sql-with-join-gtap-def []
  (assert (driver/supports? driver/*driver* :native-parameters))
  {:query (data/native-query
            {:query
             (format-honeysql
              {:select    [(identifier :checkins :id)
                           (identifier :checkins :user_id)
                           (identifier :venues :name)
                           (identifier :venues :category_id)]
               :from      [(identifier :checkins)]
               :left-join [(identifier :venues)
                           [:= (identifier :checkins :venue_id) (identifier :venues :id)]]
               :where     [:= (identifier :checkins :user_id) (hsql/raw "{{user}}")]
               :order-by  [[(identifier :checkins :id) :asc]]})

             :template_tags
             {"user" {:name         "user"
                      :display-name "User ID"
                      :type         :number
                      :required     true}}})
   :remappings {:user ["variable" ["template-tag" "user"]]}})

(defn- venue-names-native-gtap-def []
  {:query (data/native-query
            {:query
             (format-honeysql
              {:select   [(identifier :venues :name)]
               :from     [(identifier :venues)]
               :order-by [(identifier :venues :id)]})})})



(defn- run-venues-count-query []
  (qp.test/format-rows-by [int]
    (qp.test/rows
      (data/run-mbql-query venues {:aggregation [[:count]]}))))

(defn- run-checkins-count-broken-out-by-price-query []
  (qp.test/format-rows-by [#(some-> % int) int]
    (qp.test/rows
      (data/run-mbql-query checkins
        {:aggregation [[:count]]
         :order-by    [[:asc $venue_id->venues.price]]
         :breakout    [$venue_id->venues.price]}))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                MIDDLEWARE TESTS                                                |
;;; +----------------------------------------------------------------------------------------------------------------+

;; make sure that `all-table-ids` can properly find all Tables in the query, even in cases where a map has
;; a `:source-table` and some of its children also have a `:source-table`
(expect
  (data/$ids nil
    #{$$checkins $$venues $$users $$categories})
  (#'row-level-restrictions/all-table-ids
   (data/mbql-query nil
     {:source-table $$checkins
      :joins        [{:source-table $$venues}
                     {:source-query {:source-table $$users
                                     :joins        [{:source-table $$categories}]}}]})))

(defn- apply-row-level-permissions [query]
  (let [result          (qp.tu/with-everything-store
                          ((row-level-restrictions/apply-row-level-permissions identity)
                           (normalize/normalize query)))
        remove-metadata (fn remove-metadata [m]
                          (mbql.u/replace m
                            (_ :guard (every-pred map? :source-metadata))
                            (remove-metadata (dissoc &match :source-metadata))))]
    (remove-metadata result)))

;; Make sure the middleware does the correct transformation given the GTAPs we have
(mt.tu/expect-with-gtaps {:gtaps      {:checkins (checkins-user-mbql-gtap-def)
                                       :venues   (dissoc (venues-price-mbql-gtap-def) :query)}
                          :attributes {"user" 5, "price" 1}}
  (data/query checkins
    {:type       :query
     :query      {:source-query {:source-query {:source-table $$checkins
                                                :fields       [*id !default.*date *user_id *venue_id]
                                                :filter       [:> $date [:absolute-datetime
                                                                         #inst "2014-01-01T00:00:00.000000000-00:00"
                                                                         :default]]}
                                 :filter       [:= $user_id [:value 5 {:base_type     :type/Integer
                                                                       :special_type  :type/FK
                                                                       :database_type "INTEGER"}]]
                                 :fields       [*id *date *user_id *venue_id]
                                 :limit        qp.i/absolute-max-results}
                  :joins        [{:source-table $$venues
                                  :alias        "v"
                                  :strategy     :left-join
                                  :condition    [:= $venue_id &v.venues.id]}]
                  :aggregation  [[:count]]
                  :gtap?        true}
     :gtap-perms #{(perms/table-query-path (Table (data/id :venues)))
                   (perms/table-query-path (Table (data/id :checkins)))}})
  (apply-row-level-permissions
   (data/mbql-query checkins
     {:aggregation [[:count]]
      :joins       [{:source-table $$venues
                     :alias        "v"
                     :strategy     :left-join
                     :condition    [:= $venue_id &v.venues.id]}]})))

(mt.tu/expect-with-gtaps {:gtaps      {:venues (venues-category-native-gtap-def)}
                          :attributes {"cat" 50}}
  (data/query nil
    {:database (data/id)
     :type       :query
     :query      {:aggregation  [[:count]]
                  :source-query {:native "SELECT * FROM \"VENUES\" WHERE \"VENUES\".\"CATEGORY_ID\" = 50 ORDER BY \"VENUES\".\"ID\" ASC"
                                 :params nil}}
     :gtap-perms #{(perms/adhoc-native-query-path (data/id))}})
  (apply-row-level-permissions
   (data/mbql-query venues
     {:aggregation [[:count]]})))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                END-TO-END TESTS                                                |
;;; +----------------------------------------------------------------------------------------------------------------+

;; When querying with full permissions, no changes should be made
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[100]]
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-mbql-gtap-def)}
                     :attributes {"cat" 50}}
    (perms/grant-permissions! &group (perms/table-query-path (Table (data/id :venues))))
    (run-venues-count-query)))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is a native
;; query
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-native-gtap-def)}
                     :attributes {"cat" 50}}
    (run-venues-count-query)))

;; Basic test around querying a table by a user with segmented only permissions and a GTAP question that is MBQL
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-mbql-gtap-def)}
                     :attributes {"cat" 50}}
    (run-venues-count-query)))

;; When processing a query that requires a user attribute and that user attribute isn't there, throw an exception
;; letting the user know it's missing
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  "Query requires user attribute `cat`"
  (:error
   (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-mbql-gtap-def)}
                      :attributes {"something_random" 50}}
     (data/run-mbql-query venues {:aggregation [[:count]]}))))

;; Another basic test, same as above, but with a numeric string that needs to be coerced
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-mbql-gtap-def)}
                     :attributes {"cat" "50"}}
    (run-venues-count-query)))

;; Another basic test, this one uses a stringified float for the login attribute
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[3]]
  (mt.tu/with-gtaps {:gtaps      {:venues {:query      (data/mbql-query venues)
                                           :remappings {:cat ["variable" [:field-id (data/id :venues :latitude)]]}}}
                     :attributes {"cat" "34.1018"}}
    (run-venues-count-query)))

;; Tests that users can have a different parameter name in their query than they have in their user attributes
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues {:query      (:query (venues-category-native-gtap-def))
                                           :remappings {:something.different ["variable" ["template-tag" "cat"]]}}}
                     :attributes {"something.different" 50}}
    (run-venues-count-query)))

;; Make sure that you can still use a SQL-based GTAP without needing to have SQL read perms for the Database
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [["Red Medicine"] ["Stout Burgers & Beers"]]
  (qp.test/rows
    (mt.tu/with-gtaps {:gtaps {:venues (venue-names-native-gtap-def)}}
      (data/run-mbql-query venues {:limit 2}))))

;; When no card_id is included in the GTAP, should default to a query against the table, with the GTAP criteria applied
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues (dissoc (venues-category-mbql-gtap-def) :query)}
                     :attributes {"cat" 50}}
    (run-venues-count-query)))

;; Same test as above but make sure we coerce a numeric string correctly
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[10]]
  (mt.tu/with-gtaps {:gtaps      {:venues (dissoc (venues-category-mbql-gtap-def) :query)}
                     :attributes {"cat" "50"}}
    (run-venues-count-query)))

;; Users with view access to the related collection should bypass segmented permissions
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  1
  (data/with-temp-copy-of-db
    (tt/with-temp* [Collection [collection]
                    Card       [card        {:collection_id (u/get-id collection)}]]
      (mt.tu/with-group [group]
        (perms/revoke-permissions! (perms-group/all-users) (data/id))
        (perms/grant-collection-read-permissions! group collection)
        (users/with-test-user :rasta
          (count
           (qp.test/rows
             (qp/process-query
               {:database (data/id)
                :type     :query
                :query    {:source-table (data/id :venues)
                           :limit        1}
                :info     {:card-id    (u/get-id card)
                           :query-hash (byte-array 0)}}))))))))

;; This test isn't covering a row level restrictions feature, but rather checking it it doesn't break querying of a
;; card as a nested query. Part of the row level perms check is looking at the table (or card) to see if row level
;; permissions apply. This was broken when it wasn't expecting a card and only expecting resolved source-tables
(datasets/expect-with-drivers (qp.test/non-timeseries-drivers-with-feature :nested-queries)
  [[100]]
  (tt/with-temp Card [card {:dataset_query (data/mbql-query venues)}]
    (qp.test/format-rows-by [int]
      (qp.test/rows
        (users/with-test-user :rasta
          (qp/process-query
            {:database (data/id)
             :type     :query
             :query    {:source-table (format "card__%s" (u/get-id card))
                        :aggregation  [["count"]]}}))))))

;; Test that we can follow FKs to related tables and breakout by columns on those related tables. This test has
;; several things wrapped up which are detailed below

(defn- row-level-restrictions-fk-drivers
  "Drivers to test row-level restrictions against foreign keys with. Includes BigQuery, which for whatever reason does
  not normally have FK tests ran for it."
  []
  (cond-> (qp.test/non-timeseries-drivers-with-feature :nested-queries :foreign-keys)
    (tx.env/test-drivers :bigquery) (conj :bigquery)))

;; HACK - Since BigQuery doesn't formally support foreign keys (meaning we can't sync them automatically), FK tests
;; are disabled by default for BigQuery. We really want to test them here! The macros below let us "fake" FK support
;; for BigQuery.
(defn- do-enable-bigquery-fks [f]
  (let [supports? driver/supports?]
    (with-redefs [driver/supports? (fn [driver feature]
                                     (if (= [driver feature] [:bigquery :foreign-keys])
                                       true
                                       (supports? driver feature)))]
      (f))))

(defmacro ^:private enable-bigquery-fks [& body]
  `(do-enable-bigquery-fks (fn [] ~@body)))

(defn- do-with-bigquery-fks [f]
  (if-not (= driver/*driver* :bigquery)
    (f)
    (tu/with-temp-vals-in-db Field (data/id :checkins :user_id) {:fk_target_field_id (data/id :users :id)
                                                                 :special_type       "type/FK"}
      (tu/with-temp-vals-in-db Field (data/id :checkins :venue_id) {:fk_target_field_id (data/id :venues :id)
                                                                    :special_type       "type/FK"}
        (f)))))

(defmacro ^:private with-bigquery-fks [& body]
  `(do-with-bigquery-fks (fn [] ~@body)))

;; 1 - Creates a GTAP filtering question, looking for any checkins happening on or after 2014
;; 2 - Apply the `user` attribute, looking for only our user (i.e. `user_id` =  5)
;; 3 - Checkins are related to Venues, query for checkins, grouping by the Venue's price
;; 4 - Order by the Venue's price to ensure a predictably ordered response
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  [[1 10] [2 36] [3 4] [4 5]]
  (enable-bigquery-fks
   (mt.tu/with-gtaps {:gtaps      {:checkins (checkins-user-mbql-gtap-def)
                                   :venues   nil}
                      :attributes {"user" 5}}
     (with-bigquery-fks
       (run-checkins-count-broken-out-by-price-query)))))

;; Test that we're able to use a GTAP for an FK related table. For this test, the user has segmented permissions on
;; checkins and venues, so we need to apply a GTAP to the original table (checkins) in addition to the related table
;; (venues). This test uses a GTAP question for both tables
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  #{[nil 45] [1 10]}
  (enable-bigquery-fks
   (mt.tu/with-gtaps {:gtaps      {:checkins (checkins-user-mbql-gtap-def)
                                   :venues   (venues-price-mbql-gtap-def)}
                      :attributes {"user" 5, "price" 1}}
     (with-bigquery-fks
       (set (run-checkins-count-broken-out-by-price-query))))))

;; Test that the FK related table can be a "default" GTAP, i.e. a GTAP where the `card_id` is nil
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  #{[nil 45] [1 10]}
  (enable-bigquery-fks
   (mt.tu/with-gtaps {:gtaps      {:checkins (checkins-user-mbql-gtap-def)
                                   :venues   (dissoc (venues-price-mbql-gtap-def) :query)}
                      :attributes {"user" 5, "price" 1}}
     (with-bigquery-fks
       (set (run-checkins-count-broken-out-by-price-query))))))

;; Test that we have multiple FK related, segmented tables. This test has checkins with a GTAP question with venues
;; and users having the default GTAP and segmented permissions
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  #{[nil "Quentin Sören" 45] [1 "Quentin Sören" 10]}
  (enable-bigquery-fks
   (mt.tu/with-gtaps {:gtaps      {:checkins (checkins-user-mbql-gtap-def)
                                   :venues   (dissoc (venues-price-mbql-gtap-def) :query)
                                   :users    {:remappings {:user ["variable" [:field-id (data/id :users :id)]]}}}
                      :attributes {"user" 5, "price" 1}}
     (with-bigquery-fks
       (set
        (qp.test/format-rows-by [#(when % (int %)) str int]
          (qp.test/rows
            (data/run-mbql-query checkins
              {:aggregation [[:count]]
               :order-by    [[:asc $venue_id->venues.price]]
               :breakout    [$venue_id->venues.price $user_id->users.name]}))))))))

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
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-mbql-gtap-def)}
                     :attributes {"cat" 50}}
    (run-query-returning-remark
     (fn []
       ((users/user->client :rasta) :post "dataset" (data/mbql-query venues {:aggregation [[:count]]}))))))

;; Make sure that if a GTAP is in effect we can still do stuff like breakouts (#229)
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  [[1 6] [2 4]]
  (mt.tu/with-gtaps {:gtaps      {:venues (venues-category-native-gtap-def)}
                     :attributes {"cat" 50}}
    (qp.test/format-rows-by [int int]
      (qp.test/rows
        (data/run-mbql-query venues
          {:aggregation [[:count]]
           :breakout    [$price]})))))

;; If we use a parameterized SQL GTAP that joins a Table the user doesn't have access to, does it still work? (EE #230)
;; If we pass the query in directly without anything that would require nesting it, it should work
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  [[2  1 "Bludso's BBQ" 5]
   [72 1 "Red Medicine" 4]]
  (qp.test/format-rows-by [int int identity int]
    (qp.test/rows
      (mt.tu/with-gtaps {:gtaps      {:checkins (parameterized-sql-with-join-gtap-def)}
                         :attributes {"user" 1}}
        (data/run-mbql-query checkins
          {:limit 2})))))


;; #230: If we modify the query in a way that would cause the original to get nested as a source query, do things work?
(datasets/expect-with-drivers (row-level-restrictions-fk-drivers)
  [[5 69]]
  (qp.test/format-rows-by [int int]
    (qp.test/rows
      (mt.tu/with-gtaps {:gtaps      {:checkins (parameterized-sql-with-join-gtap-def)}
                         :attributes {"user" 5}}
        (data/run-mbql-query checkins
          {:aggregation [[:count]]
           :breakout    [$user_id]})))))
