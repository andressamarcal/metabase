(ns metabase.audit.pages.databases
  (:require [honeysql.core :as hsql]
            [metabase.models.database :refer [Database]]
            [metabase.util.honeysql-extensions :as hx]
            [toucan.db :as db]))

;; SELECT
;;   db.name AS "database",
;;   count(*) AS queries,
;;   avg(qe.running_time) AS avg_running_time
;; FROM query_execution qe
;; LEFT JOIN report_card card
;;   ON qe.card_id = card.id AND qe.card_id IS NOT NULL
;; LEFT JOIN metabase_table t
;;   ON card.table_id = t.id AND card.table_id IS NOT NULL
;; LEFT JOIN metabase_database db
;;   ON t.db_id = db.id
;; WHERE db.id IS NOT NULL
;; GROUP BY db.id
;; ORDER BY lower(db.name) ASC
(defn ^:internal-query-fn total-query-executions-by-db
  []
  {:metadata [[:database         {:display_name "Database",               :base_type :type/Text}]
              [:queries          {:display_name "Queries",                :base_type :type/Integer}]
              [:avg_running_time {:display_name "Avg. Running Time (ms)", :base_type :type/Decimal}]]
   :results  (db/query
              {:select    [[:db.name :database]
                           [:%count.* :queries]
                           [:%avg.qe.running_time :avg_running_time]]
               :from      [[:query_execution :qe]]
               :left-join [[:report_card :card]     [:= :qe.card_id :card.id]
                           [:metabase_table :t]     [:= :card.table_id :t.id]
                           [:metabase_database :db] [:= :t.db_id :db.id]]
               :where     [:and
                           [:not= :qe.card_id nil]
                           [:not= :card.table_id nil]
                           [:not= :db.id nil]]
               :group-by  [:db.id]
               :order-by  [[:%lower.db.name :asc]]})})

;; WITH qx AS (
;;  SELECT CAST(qe.started_at AS date) AS day, card.database_id, count(*) AS count
;;  FROM query_execution qe
;;  LEFT JOIN report_card card
;;    ON qe.card_id = card.id
;;  WHERE qe.card_id IS NOT NULL
;;    AND card.database_id IS NOT NULL
;;  GROUP BY CAST(qe.started_at AS date), card.database_id
;;  ORDER BY CAST(qe.started_at AS date) ASC, card.database_id ASC
;; )
;;
;; SELECT qx.day, qx.database_id, db.name AS database_name, qx.count
;; FROM qx
;; LEFT JOIN metabase_database db
;;   ON qx.database_id = db.id
;; ORDER BY qx.day ASC, qx.database_id ASC
(defn ^:internal-query-fn query-executions-per-db-per-day
  []
  {:metadata [[:day           {:display_name "Date",          :base_type :type/Date}]
              [:database_id   {:display_name "Database ID",   :base_type :type/Integer, :remapped_to :database_name}]
              [:database_name {:display_name "Database Name", :base_type :type/Name,    :remapped_from :database_id}]
              [:count         {:display_name "Count",         :base_type :type/Integer}]]
   :results  (db/query
              {:with      [[:qx {:select    [[(hx/cast :date :qe.started_at) :day]
                                             :card.database_id
                                             [:%count.* :count]]
                                 :from      [[:query_execution :qe]]
                                 :left-join [[:report_card :card] [:= :qe.card_id :card.id]]
                                 :where     [:and
                                             [:not= :qe.card_id nil]
                                             [:not= :card.database_id nil]]
                                 :group-by  [(hx/cast :date :qe.started_at) :card.database_id]
                                 :order-by  [[(hx/cast :date :qe.started_at) :asc]
                                             [:card.database_id :asc]]}]]
               :select    [:qx.day
                           :qx.database_id
                           [:db.name :database_name]
                           :qx.count]
               :from      [:qx]
               :left-join [[:metabase_database :db] [:= :qx.database_id :db.id]]
               :order-by  [[:qx.day :asc]
                           [:%lower.db.name :asc]
                           [:qx.database_id :asc]]})})

;; WITH counts AS (
;;     SELECT db_id AS id, count(DISTINCT "schema") AS schemas, count(*) AS tables
;;     FROM metabase_table
;;     GROUP BY db_id
;; )
;;
;; SELECT
;;   db.name AS title,
;;   db.created_at AS added_on,
;;   db.metadata_sync_schedule AS sync_schedule,
;;   counts.schemas AS schemas,
;;   counts.tables AS tables,
;; FROM metabase_database db
;; LEFT JOIN counts
;;   ON db.id = counts.id
;; ORDER BY lower(name) ASC
(defn- ^:internal-query-fn table []
  ;; TODO - Should we convert sync_schedule from a cron string into English? Not sure that's going to be feasible for
  ;; really complicated schedules
  {:metadata [[:title         {:display_name "Title",         :base_type :type/Text}]
              [:added_on      {:display_name "Added On",      :base_type :type/DateTime}]
              [:sync_schedule {:display_name "Sync Schedule", :base_type :type/Text}]
              [:schemas       {:display_name "Schemas",       :base_type :type/Integer}]
              [:tables        {:display_name "Tables",        :base_type :type/Integer}]]
   :results  (db/query
              {:with      [[:counts {:select   [[:db_id :id]
                                                [(hsql/call :distinct-count :schema) :schemas]
                                                [:%count.* :tables]]
                                     :from     [:metabase_table]
                                     :group-by [:db_id]}]]
               :select    [[:db.name :title]
                           [:db.created_at :added_on]
                           [:db.metadata_sync_schedule :sync_schedule]
                           [:counts.schemas :schemas]
                           [:counts.tables :tables]]
               :from      [[:metabase_database :db]]
               :left-join [:counts [:= :db.id :counts.id]]
               :order-by  [[:%lower.name :asc]]})})
