(ns metabase.audit.pages.queries
  (:require [metabase.util.honeysql-extensions :as hx]
            [toucan.db :as db]))

;; SELECT CAST(started_at AS date) AS day, count(*) AS views, avg(running_time) AS avg_running_time
;; FROM query_execution
;; GROUP BY CAST(started_at AS date)
;; ORDER BY CAST(started_at AS date) ASC
(defn ^:internal-query-fn views-and-avg-execution-time-by-day
  "Query that returns data for a two-series timeseries chart with number of queries ran and average query running time
  broken out by day."
  []
  {:metadata [[:day              {:display_name "Date",                   :base_type :type/Date}]
              [:views            {:display_name "Views",                  :base_type :type/Integer}]
              [:avg_running_time {:display_name "Avg. Running Time (ms)", :base_type :type/Decimal}]]
   :results  (db/query
              {:select   [[(hx/cast :date :started_at) :day]
                          [:%count.* :views]
                          [:%avg.running_time :avg_running_time]]
               :from     [:query_execution]
               :group-by [(hx/cast :date :started_at)]
               :order-by [[(hx/cast :date :started_at) :asc]]})})

;; -- we are currently prepending ID to card name to make sure it is unique and FE does not combine rows for Cards with
;; -- the same name. See discussion in `dashboards/most-common-questions`
;; SELECT (c.id::text || ' ' || c.name) AS card_name, count(*) AS executions
;; FROM query_execution qe
;; LEFT JOIN report_card c
;;   ON qe.card_id = c.id
;; WHERE qe.card_id IS NOT NULL
;; GROUP BY c.id
;; ORDER BY executions DESC
;; LIMIT 10
(defn ^:internal-query-fn most-popular
  "Query that returns the 10 most-popular Cards based on number of query executions, in descending order."
  []
  {:metadata [[:card_name  {:display_name "Card",       :base_type :type/Title}]
              [:executions {:display_name "Executions", :base_type :type/Integer}]]
   :results  (db/query
              {:select    [[(hx/concat (hx/cast :text :c.id) (hx/literal " ") :c.name) :card_name]
                           [:%count.* :executions]]
               :from      [[:query_execution :qe]]
               :left-join [[:report_card :c] [:= :qe.card_id :c.id]]
               :where     [:not= :qe.card_id nil]
               :group-by  [:c.id]
               :order-by  [[:executions :desc]]
               :limit     10})})

;; SELECT (c.id::text || ' ' || c.name) AS card_name, avg(running_time) AS avg_running_time
;; FROM query_execution qe
;; LEFT JOIN report_card c
;;   ON qe.card_id = c.id
;; WHERE qe.card_id IS NOT NULL
;; GROUP BY c.id
;; ORDER BY avg_running_time DESC
;; LIMIT 10
(defn ^:internal-query-fn slowest
  "Query that returns the 10 slowest-running Cards based on average query execution time, in descending order."
  []
  {:metadata [[:card_name        {:display_name "Card",                      :base_type :type/Title}]
              [:avg_running_time {:display_name "Average Running Time (ms)", :base_type :type/Decimal}]]
   :results  (db/query
              {:select    [[(hx/concat (hx/cast :text :c.id) (hx/literal " ") :c.name) :card_name]
                           [:%avg.running_time :avg_running_time]]
               :from      [[:query_execution :qe]]
               :left-join [[:report_card :c] [:= :qe.card_id :c.id]]
               :where     [:not= :qe.card_id nil]
               :group-by  [:c.id]
               :order-by  [[:avg_running_time :desc]]
               :limit     10})})
