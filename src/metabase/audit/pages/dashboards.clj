(ns metabase.audit.pages.dashboards
  "Dashboards overview page."
  (:require [honeysql.core :as hsql]
            [metabase.audit.pages.common :as audit-common]
            [metabase.util :as u]
            [metabase.util
             [honeysql-extensions :as hx]
             [urls :as urls]]
            [schema.core :as s]
            [toucan.db :as db]))

;; SELECT CAST("timestamp" AS date) AS day, count(*) AS views
;; FROM view_log
;; WHERE model = 'dashboard'
;; GROUP BY CAST("timestamp" AS date)
;; ORDER BY CAST("timestamp" AS date) ASC
(defn ^:deprecated ^:internal-query-fn views-per-day
  "DEPRECATED: use `views-and-saves-by-time ` instead."
  []
  (println (u/format-color 'red
               (str "WARNING: metabase.audit.pages.dashboards/views-per-day is deprecated. "
                    "Use views-and-saves-by-time instead. "
                    "This will be removed in the near future.")))
  {:metadata [[:day   {:display_name "Date",  :base_type :type/Date}]
              [:views {:display_name "Views", :base_type :type/Integer}]]
   :results  (db/query
              {:select   [[(hx/cast :date :timestamp) :day]
                          [:%count.* :views]]
               :from     [:view_log]
               :where    [:= :model (hx/literal "dashboard")]
               :group-by [(hx/cast :date :timestamp)]
               :order-by [(hx/cast :date :timestamp)]})})

(s/defn ^:internal-query-fn views-and-saves-by-time
  "Two-series timeseries that includes total number of Dashboard views and saves broken out by a `datetime-unit`."
  [datetime-unit :- audit-common/DateTimeUnitStr]
  {:metadata [[:date  {:display_name "Date",  :base_type (audit-common/datetime-unit-str->base-type datetime-unit)}]
              [:views {:display_name "Views", :base_type :type/Integer}]
              [:saves {:display_name "Saves", :base_type :type/Integer}]]
   :results (db/query
             {:with      [[:views {:select   [[(audit-common/grouped-datetime datetime-unit :timestamp) :date]
                                              [:%count.* :count]]
                                   :from     [:view_log]
                                   :where    [:= :model (hx/literal "dashboard")]
                                   :group-by [(audit-common/grouped-datetime datetime-unit :timestamp)]
                                   :order-by [[(audit-common/grouped-datetime datetime-unit :timestamp) :asc]]}]
                          [:saves {:select   [[(audit-common/grouped-datetime datetime-unit :created_at) :date]
                                              [:%count.* :count]]
                                   :from     [:report_dashboard]
                                   :group-by [(audit-common/grouped-datetime datetime-unit :created_at)]
                                   :order-by [[(audit-common/grouped-datetime datetime-unit :created_at) :asc]]}]]
              :select    [:views.date
                          [:views.count :views]
                          [:saves.count :saves]]
              :from      [:views]
              :full-join [:saves [:= :views.date :saves.date]]
              :order-by  [[:views.date :asc]]})})

;; SELECT d.id AS dashboard_id, d.name AS dashboard_name, count(*) AS views
;; FROM view_log vl
;; LEFT JOIN report_dashboard d
;;   ON vl.model_id = d.id
;; WHERE vl.model = 'dashboard'
;; GROUP BY d.id
;; ORDER BY count(*) DESC
;; LIMIT 10
(defn ^:internal-query-fn most-popular
  "Query that returns the 10 Dashboards that have the most views, in descending order."
  []
  {:metadata [[:dashboard_id   {:display_name "Dashboard ID",          :base_type :type/Integer, :remapped_to   :dashboard_name}]
              [:dashboard_name {:display_name "Dashboard",             :base_type :type/Title,   :remapped_from :dashboard_id}]
              [:views          {:display_name "Views",     :base_type :type/Integer}]]
   :results  (db/query
              {:select    [[:d.id :dashboard_id]
                           [:d.name :dashboard_name]
                           [:%count.* :views]]
               :from      [[:view_log :vl]]
               :left-join [[:report_dashboard :d] [:= :vl.model_id :d.id]]
               :where     [:= :vl.model (hx/literal "dashboard")]
               :group-by  [:d.id]
               :order-by  [[:%count.* :desc]]
               :limit     10})})

;; WITH card_running_time AS (
;;     SELECT qe.card_id, avg(qe.running_time) AS avg_running_time
;;     FROM query_execution qe
;;     WHERE qe.card_id IS NOT NULL
;;     GROUP BY qe.card_id
;; )
;;
;; SELECT d.id AS dashboard_id, d.name AS dashboard_name, max(rt.avg_running_time) AS max_running_time
;; FROM report_dashboardcard dc
;; LEFT JOIN card_running_time rt
;;   ON dc.card_id = rt.card_id
;; LEFT JOIN report_dashboard d
;;   ON dc.dashboard_id = d.id
;; GROUP BY d.id
;; ORDER BY max_running_time DESC
;; LIMIT 10
(defn- ^:internal-query-fn slowest
  "Query that returns the 10 Dashboards that have the slowest average execution times, in descending order."
  []
  {:metadata [[:dashboard_id     {:display_name "Dashboard ID",          :base_type :type/Integer, :remapped_to   :dashboard_name}]
              [:dashboard_name   {:display_name "Dashboard",             :base_type :type/Title,   :remapped_from :dashboard_id}]
              [:max_running_time {:display_name "Slowest Question (ms)", :base_type :type/Decimal}]]
   :results  (db/query
              {:with      [[:card_running_time {:select   [:qe.card_id
                                                           [:%avg.qe.running_time :avg_running_time]]
                                                :from     [[:query_execution :qe]]
                                                :where    [:not= :qe.card_id nil]
                                                :group-by [:qe.card_id]}]]
               :select    [[:d.id :dashboard_id]
                           [:d.name :dashboard_name]
                           [:%max.rt.avg_running_time :max_running_time]]
               :from      [[:report_dashboardcard :dc]]
               :left-join [[:card_running_time :rt] [:= :dc.card_id :rt.card_id]
                           [:report_dashboard :d]   [:= :dc.dashboard_id :d.id]]
               :group-by  [:d.id]
               :order-by  [[:max_running_time :desc]]
               :limit     10})})

;; SELECT c.id AS card_id, c.name AS card_name, count(*) AS "count"
;; FROM report_dashboardcard dc
;; LEFT JOIN report_card c
;;   ON c.id = dc.card_id
;; GROUP BY c.id
;; ORDER BY count(*) DESC
;; LIMIT 10
(defn- ^:internal-query-fn most-common-questions
  "Query that returns the 10 Cards that appear most often in Dashboards, in descending order."
  []
  {:metadata [[:card_id   {:display_name "Card ID", :base_type :type/Integer, :remapped_to   :card_name}]
              [:card_name {:display_name "Card",    :base_type :type/Title,   :remapped_from :card_id}]
              [:count     {:display_name "Count",   :base_type :type/Integer}]]
   :results  (db/query
              {:select    [[:c.id :card_id]
                           [:c.name :card_name]
                           [:%count.* :count]]
               :from      [[:report_dashboardcard :dc]]
               :left-join [[:report_card :c]
                           [:= :c.id :dc.card_id]]
               :group-by  [:c.id]
               :order-by  [[:%count.* :desc]]
               :limit     10})})

;; WITH card_count AS (
;;   SELECT dashboard_id, count(*) AS card_count
;;   FROM report_dashboardcard
;;   GROUP BY dashboard_id
;; ),
;;
;; card_avg_execution_time AS (
;;   SELECT card_id, avg(running_time) AS avg_running_time
;;   FROM query_execution
;;   WHERE card_id IS NOT NULL
;;   GROUP BY card_id
;; ),
;;
;; avg_execution_time AS (
;;   SELECT dc.dashboard_id, avg(cxt.avg_running_time) AS avg_running_time
;;   FROM report_dashboardcard dc
;;   LEFT JOIN card_avg_execution_time cxt
;;     ON dc.card_id = cxt.card_id
;;   GROUP BY dc.dashboard_id
;; ),
;;
;; views AS (
;;   SELECT model_id AS dashboard_id, count(*) AS view_count
;;   FROM view_log
;;   WHERE model = 'dashboard'
;;   GROUP BY model_id
;; )
;;
;; SELECT
;;   d.id AS dashboard_id
;;   d.name AS title,
;;   (u.first_name || ' ' || u.last_name) AS saved_by,
;;   d.created_at AS saved_on,
;;   d.updated_at AS last_edited_on,
;;   cc.card_count AS cards,
;;   (CASE WHEN d.public_uuid IS NOT NULL THEN ('http://localhost:3000/public/dashboard/' || d.public_uuid) END)
;;     AS public_link,
;;   axt.avg_running_time AS average_execution_time_ms,
;;   v.view_count AS total_views
;; FROM report_dashboard d
;; LEFT JOIN core_user u
;;   ON d.creator_id = u.id
;; LEFT JOIN card_count cc
;;   ON d.id = cc.dashboard_id
;; LEFT JOIN avg_execution_time axt
;;   ON d.id = axt.dashboard_id
;; LEFT JOIN views AS v
;;   ON d.id = v.dashboard_id
;; ORDER BY lower(d.name) ASC, dashboard_id ASC
(defn ^:internal-query-fn table
  "Internal audit app query powering a table of different Dashboards with lots of extra info about them."
  []
  {:metadata [[:dashboard_id              {:display_name "Dashboard ID",         :base_type :type/Integer, :remapped_to   :title}]
              [:title                     {:display_name "Title",                :base_type :type/Title,   :remapped_from :dashboard_id}]
              [:saved_by                  {:display_name "Saved by",             :base_type :type/Text}]
              [:saved_on                  {:display_name "Saved on",             :base_type :type/DateTime}]
              [:last_edited_on            {:display_name "Last edited on",       :base_type :type/DateTime}]
              [:cards                     {:display_name "Cards",                :base_type :type/Integer}]
              [:public_link               {:display_name "Public Link",          :base_type :type/URL}]
              [:average_execution_time_ms {:display_name "Avg. exec. time (ms)", :base_type :type/Decimal}]
              [:total_views               {:display_name "Total views",          :base_type :type/Integer}]]
   :results (db/query
             {:with      [[:card_count {:select   [:dashboard_id
                                                   [:%count.* :card_count]]
                                        :from     [:report_dashboardcard]
                                        :group-by [:dashboard_id]}]
                          [:card_avg_execution_time {:select   [:card_id
                                                                [:%avg.running_time :avg_running_time]]
                                                     :from     [:query_execution]
                                                     :where    [:not= :card_id nil]
                                                     :group-by [:card_id]}]
                          [:avg_execution_time {:select    [:dc.dashboard_id
                                                            [:%avg.cxt.avg_running_time :avg_running_time]]
                                                :from      [[:report_dashboardcard :dc]]
                                                :left-join [[:card_avg_execution_time :cxt] [:= :dc.card_id :cxt.card_id]]
                                                :group-by  [:dc.dashboard_id]}]
                          [:views {:select   [[:model_id :dashboard_id]
                                              [:%count.* :view_count]]
                                   :from     [:view_log]
                                   :where    [:= :model (hx/literal "dashboard")]
                                   :group-by [:model_id]}]]
              :select    [[:d.id :dashboard_id]
                          [:d.name :title]
                          [(audit-common/user-full-name :u) :saved_by]
                          [:d.created_at :saved_on]
                          [:d.updated_at :last_edited_on]
                          [:cc.card_count :cards]
                          [(hsql/call :case
                             [:not= :d.public_uuid nil]
                             (hx/concat (urls/public-dashboard-prefix) :d.public_uuid))
                           :public_link]
                          [:axt.avg_running_time :average_execution_time_ms]
                          [:v.view_count :total_views]]
              :from      [[:report_dashboard :d]]
              :left-join [[:core_user :u]            [:= :d.creator_id :u.id]
                          [:card_count :cc]          [:= :d.id :cc.dashboard_id]
                          [:avg_execution_time :axt] [:= :d.id :axt.dashboard_id]
                          [:views :v]                [:= :d.id :v.dashboard_id]]
              :order-by  [[:%lower.d.name :asc]
                          [:dashboard_id :asc]]})})
