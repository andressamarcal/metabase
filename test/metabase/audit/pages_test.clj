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

(defn- test-query [varr objects]
  (let [query (varr->query varr objects)]
    (testing (format "%s %s:%d" varr (ns-name (:ns (meta varr))) (:line (meta varr)))
      (testing (format "\nquery = %s" (pr-str query))
        (let [result ((mt/user->client :crowberto) :post 202 "dataset" query)]
          (is (= (:status result)
                 "completed")
              (format "result = %s" (u/pprint-to-str result))))))))

(deftest all-queries-test
  ;; skip for now with MySQL because we rely heavily on CTEs and MySQL 5.x doesn't support CTEs. We test CI with
  ;; MySQL 5.x. I have manually verified these queries do work correctly with versions > 5.x.
  ;;
  ;; TODO - come up with a way to test these on CI
  (when-not (= :mysql (mdb/db-type))
    (mt/with-temp* [Database      [database]
                    Table         [table {:db_id (u/get-id database)}]
                    Card          [card {:table_id (u/get-id table), :database_id (u/get-id database)}]
                    Dashboard     [dash]
                    DashboardCard [_ {:card_id (u/get-id card), :dashboard_id (u/get-id dash)}]]
      (metastore-test/with-metastore-token-features #{:audit-app}
        (doseq [varr (all-queries)]
          (test-query varr {:database database, :table table, :card card, :dash dash}))))))
