(ns metabase.audit.pages.queries
  (:require [metabase.audit.pages.common :as common]
            [metabase.util.honeysql-extensions :as hx]))

(defn ^:internal-query-fn ^:deprecated  views-and-avg-execution-time-by-day
  "Query that returns data for a two-series timeseries chart with number of queries ran and average query running time
  broken out by day."
  []
  {:metadata [[:day              {:display_name "Date",                   :base_type :type/Date}]
              [:views            {:display_name "Views",                  :base_type :type/Integer}]
              [:avg_running_time {:display_name "Avg. Running Time (ms)", :base_type :type/Decimal}]]
   :results  (common/query
              {:select   [[(hx/cast :date :started_at) :day]
                          [:%count.* :views]
                          [:%avg.running_time :avg_running_time]]
               :from     [:query_execution]
               :group-by [(hx/cast :date :started_at)]
               :order-by [[(hx/cast :date :started_at) :asc]]})})

(defn ^:internal-query-fn most-popular
  "Query that returns the 10 most-popular Cards based on number of query executions, in descending order."
  []
  {:metadata [[:card_id    {:display_name "Card ID",    :base_type :type/Integer, :remapped_to   :card_name}]
              [:card_name  {:display_name "Card",       :base_type :type/Title,   :remapped_from :card_id}]
              [:executions {:display_name "Executions", :base_type :type/Integer}]]
   :results  (common/query
              {:select   [[:c.id :card_id]
                          [:c.name :card_name]
                          [:%count.* :executions]]
               :from     [[:query_execution :qe]]
               :join     [[:report_card :c] [:= :qe.card_id :c.id]]
               :group-by [:c.id]
               :order-by [[:executions :desc]]
               :limit    10})})

(defn ^:internal-query-fn ^:deprecated slowest
  "Query that returns the 10 slowest-running Cards based on average query execution time, in descending order."
  []
  {:metadata [[:card_id          {:display_name "Card ID",                :base_type :type/Integer, :remapped_to   :card_name}]
              [:card_name        {:display_name "Card",                   :base_type :type/Title,   :remapped_from :card_id}]
              [:avg_running_time {:display_name "Avg. Running Time (ms)", :base_type :type/Decimal}]]
   :results  (common/query
              {:select   [[:c.id :card_id]
                          [:c.name :card_name]
                          [:%avg.running_time :avg_running_time]]
               :from     [[:query_execution :qe]]
               :join     [[:report_card :c] [:= :qe.card_id :c.id]]
               :group-by [:c.id]
               :order-by [[:avg_running_time :desc]]
               :limit    10})})
