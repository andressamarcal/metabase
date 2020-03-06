(ns metabase.audit.pages-test
  (:require [clojure
             [string :as str]
             [test :refer :all]]
            [clojure.java.classpath :as classpath]
            [clojure.tools.namespace.find :as ns-find]
            [metabase
             [db :as mdb]
             [models :refer [Card Dashboard DashboardCard Database Table]]
             [test :as mt]
             [util :as u]]
            [metabase.audit.pages.users :as pages.users]
            [metabase.plugins.classloader :as classloader]
            [metabase.public-settings.metastore-test :as metastore-test]
            [metabase.query-processor.util :as qp-util]
            [metabase.test.fixtures :as fixtures]))

(use-fixtures :once (fixtures/initialize :db))

(deftest preconditions-test
  (classloader/require 'metabase.audit.pages.dashboards)
  (testing "the query should exist"
    (is (some? (resolve (symbol "metabase.audit.pages.dashboards/most-popular-with-avg-speed")))))

  (testing "test that a query will fail if not ran by an admin"
    (metastore-test/with-metastore-token-features #{:audit-app}
      (is (= {:status "failed", :error "You don't have permissions to do that."}
             (-> ((mt/user->client :lucky) :post 202 "dataset"
                  {:type :internal
                   :fn   "metabase.audit.pages.dashboards/most-popular-with-avg-speed"})
                 (select-keys [:status :error]))))))

  (testing "ok, now try to run it. Should fail because we don't have audit-app enabled"
    (metastore-test/with-metastore-token-features nil
      (is (= {:status "failed", :error "Audit App queries are not enabled on this instance."}
             (-> ((mt/user->client :crowberto) :post 202 "dataset"
                  {:type :internal
                   :fn   "metabase.audit.pages.dashboards/most-popular-with-avg-speed"})
                 (select-keys [:status :error])))))))

(defn- all-queries []
  (for [ns-symb  (ns-find/find-namespaces (classpath/system-classpath))
        :when    (str/starts-with? (name ns-symb) "metabase.audit.pages")
        [_ varr] (do (classloader/require ns-symb)
                     (ns-interns ns-symb))
        :when    (:internal-query-fn (meta varr))]
    varr))

(defn- varr->query [varr {:keys [database table card dash]}]
  (let [mta     (meta varr)
        fn-str  (str (ns-name (:ns mta)) "/" (:name mta))
        arglist (mapv keyword (first (:arglists mta)))]
    {:type :internal
     :fn   fn-str
     :args (for [arg arglist]
             (case arg
               :datetime-unit "day"
               :dashboard-id  (u/get-id dash)
               :card-id       (u/get-id card)
               :user-id       (mt/user->id :crowberto)
               :database-id   (u/get-id database)
               :table-id      (u/get-id table)
               :model         "card"
               :query-hash    (qp-util/query-hash {:database 1, :type :native})))}))

(defn- run-query [varr objects]
  (let [query (varr->query varr objects)]
    (testing (format "%s %s:%d" varr (ns-name (:ns (meta varr))) (:line (meta varr)))
      (testing (format "\nquery = %s" (pr-str query))
        ((mt/user->client :crowberto) :post 202 "dataset" query)))))

(defn- do-with-temp-objects [f]
  (mt/with-temp* [Database      [database]
                  Table         [table {:db_id (u/get-id database)}]
                  Card          [card {:table_id (u/get-id table), :database_id (u/get-id database)}]
                  Dashboard     [dash]
                  DashboardCard [_ {:card_id (u/get-id card), :dashboard_id (u/get-id dash)}]]
    (f {:database database, :table table, :card card, :dash dash})))

(defmacro ^:private with-temp-objects [[objects-binding] & body]
  `(do-with-temp-objects (fn [~objects-binding] ~@body)))

(deftest all-queries-test
  ;; skip for now with MySQL because we rely heavily on CTEs and MySQL 5.x doesn't support CTEs. We test CI with
  ;; MySQL 5.x. I have manually verified these queries do work correctly with versions > 5.x.
  ;;
  ;; TODO - come up with a way to test these on CI
  (when-not (= :mysql (mdb/db-type))
    (with-temp-objects [objects]
      (metastore-test/with-metastore-token-features #{:audit-app}
        (doseq [varr (all-queries)
                :let [result (run-query varr objects)]]
          (is (= (:status result)
                 "completed")
              (format "result = %s" (u/pprint-to-str result))))))))

(deftest results-test
  (testing "Make sure at least one of the queries gives us correct results."
    (when-not (= :mysql (mdb/db-type))
      (metastore-test/with-metastore-token-features #{:audit-app}
        (let [test-user-ids (set (map mt/user->id [:crowberto :rasta :trashbird :lucky]))
              result        (run-query #'pages.users/query-execution-time-per-user nil)]
          (testing "cols"
            (is (= [{:display_name "User ID", :base_type "type/Integer" :remapped_to "name" :name "user_id"}
                    {:display_name "Name", :base_type "type/Name" :remapped_from "user_id" :name "name"}
                    {:display_name "Total Execution Time (ms)", :base_type "type/Decimal" :name "execution_time_ms"}]
                   (mt/cols result))))
          (testing "rows"
            (is (= [[(mt/user->id :crowberto) "Crowberto Corv" true]
                    [(mt/user->id :lucky)     "Lucky Pigeon" true]
                    [(mt/user->id :rasta)     "Rasta Toucan" true]
                    [(mt/user->id :trashbird) "Trash Bird" true]]
                   (->> (mt/rows result)
                        (filter #(test-user-ids (first %)))
                        (sort-by second)
                        (mt/format-rows-by [identity identity int?]))))))))))

;; NOCOMMIT
(defn x []
  (metastore-test/with-metastore-token-features #{:audit-app}
    (let [expected      (metabase.audit.pages.users/query-execution-time-per-user)
          expected-cols (for [[k m] (:metadata expected)]
                          (assoc m :name (name k)))
          result        (run-query #'pages.users/query-execution-time-per-user nil)
          test-user-ids (set (map mt/user->id [:crowberto :rasta :trashbird :lucky]))]
      (testing "cols"
        (is (= [{:display_name "User ID", :base_type "type/Integer" :remapped_to "name" :name "user_id"}
                {:display_name "Name", :base_type "type/Name" :remapped_from "user_id" :name "name"}
                {:display_name "Total Execution Time (ms)", :base_type "type/Decimal" :name "execution_time_ms"}]
               (mt/cols result))))
      (testing "rows"
        (is (= [[(mt/user->id :crowberto) "Crowberto Corv" true]
                [(mt/user->id :lucky)     "Lucky Pigeon" true]
                [(mt/user->id :rasta)     "Rasta Toucan" true]
                [(mt/user->id :trashbird) "Trash Bird" true]]
               (->> (mt/rows result)
                    (filter #(test-user-ids (first %)))
                    (sort-by second)
                    (mt/format-rows-by [identity identity int?]))))))))
