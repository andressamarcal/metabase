(ns metabase.audit.pages-test
  (:require [clojure.java.classpath :as classpath]
            [clojure.string :as str]
            [clojure.tools.namespace.find :as ns-find]
            [expectations :refer [expect]]
            [metabase
             [db :as mdb]
             [util :as u]]
            [metabase.models
             [card :refer [Card]]
             [dashboard :refer [Dashboard]]
             [dashboard-card :refer [DashboardCard]]
             [database :refer [Database]]
             [table :refer [Table]]]
            [metabase.public-settings.metastore-test :as metastore-test]
            [metabase.query-processor.util :as qp-util]
            [metabase.test.data :as data]
            [metabase.test.data.users :as test-users]
            [toucan.util.test :as tt]))

;; test that a query will fail if not ran by an admin
(expect
  {:status "failed", :error "You don't have permissions to do that."}
  (metastore-test/with-metastore-token-features #{:audit-app}
    (require 'metabase.audit.pages.dashboards)
    ;; make sure the query actually exists
    (assert (resolve (symbol "metabase.audit.pages.dashboards/most-popular-with-avg-speed")))
    ;; ok, now try to run it. Should fail because you must be an admin to run audit-app queries
    (-> ((test-users/user->client :lucky) :post 202 "dataset"
         {:type :internal
          :fn   "metabase.audit.pages.dashboards/most-popular-with-avg-speed"})
        (select-keys [:status :error]))))


;; test that a query will be disallowed if audit app is not enabled
(expect
  {:status "failed", :error "Audit App queries are not enabled on this instance."}
  (metastore-test/with-metastore-token-features nil
    (require 'metabase.audit.pages.dashboards)
    ;; make sure the query actually exists
    (assert (resolve (symbol "metabase.audit.pages.dashboards/most-popular-with-avg-speed")))
    ;; ok, now try to run it. Should fail because we don't have audit-app enabled
    (-> ((test-users/user->client :crowberto) :post 202 "dataset"
         {:type :internal
          :fn   "metabase.audit.pages.dashboards/most-popular-with-avg-speed"})
        (select-keys [:status :error]))))


(defn- test-query-fn [fn-str arglist]
  ;; make sure sample DB is already loaded before running these tests, otherwise we get some weird behavior where
  ;; queries randomly fail because it cannot lock SYS table (?)
  (data/db)
  (tt/with-temp* [Database      [database]
                  Table         [table {:db_id (u/get-id database)}]
                  Card          [card {:table_id (u/get-id table), :database_id (u/get-id database)}]
                  Dashboard     [dash]
                  DashboardCard [_ {:card_id (u/get-id card), :dashboard_id (u/get-id dash)}]]
    (metastore-test/with-metastore-token-features #{:audit-app}
      (let [result ((test-users/user->client :crowberto) :post 202 "dataset"
                    {:type :internal
                     :fn   fn-str
                     :args (for [arg arglist]
                             (case arg
                               :datetime-unit "day"
                               :dashboard-id  (u/get-id dash)
                               :card-id       (u/get-id card)
                               :user-id       (test-users/user->id :crowberto)
                               :database-id   (u/get-id database)
                               :table-id      (u/get-id table)
                               :model         "card"
                               :query-hash    (qp-util/query-hash {:database 1, :type :native})))})]
        (or (= (:status result) "completed")
            (println (u/pprint-to-str 'red result)))))))


(defmacro ^:private test-all []
  ;; skip for now with MySQL because we rely heavily on CTEs and MySQL 5.x doesn't support CTEs. We test CI with
  ;; MySQL 5.x. I have manually verified these queries do work correctly with versions > 5.x.
  ;;
  ;; TODO - come up with a way to test these on CI
  (when-not (= :mysql (mdb/db-type))
    `(do
       ~@(for [ns-symb     (ns-find/find-namespaces (classpath/system-classpath))
               :when       (str/starts-with? (name ns-symb) "metabase.audit.pages")
               [symb varr] (do (require ns-symb)
                               (ns-interns ns-symb))
               :when       (:internal-query-fn (meta varr))]
           `(expect
              (test-query-fn ~(str ns-symb "/" symb) ~(mapv keyword (first (:arglists (meta varr))))))))))

(test-all)
