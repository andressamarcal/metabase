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

;; SELECT
;;     -- one entry for each Database like this:
;;     count(CASE WHEN db.id = 1 THEN 1 END) AS "Sample Dataset",
;;     cast(started_at AS date) AS day
;; FROM query_execution qe
;; LEFT JOIN report_card card
;;   ON qe.card_id = card.id AND qe.card_id IS NOT NULL
;; LEFT JOIN metabase_table t
;;   ON card.table_id = t.id AND card.table_id IS NOT NULL
;; LEFT JOIN metabase_database db
;;   ON t.db_id = db.id
;; WHERE db.id IS NOT NULL
;; GROUP BY cast(started_at AS date)
;; ORDER BY cast(started_at AS date) ASC
(defn ^:internal-query-fn query-executions-per-db-per-day
  []
  {:metadata (conj
              (vec (for [[db-name db-id] (db/select-field->id :name Database {:order-by [:%lower.name]})]
                     [db-name {:display_name db-name, :base_type :type/Text}]))
              [:day {:display_name "Day", :base_type :type/DateTime}])
   :results  (db/query
              {:select    (conj
                           (vec (for [[db-name db-id] (db/select-field->id :name Database {:order-by [:%lower.name]})]
                                  [(hsql/call :count (hsql/call :case [:= :db.id db-id] 1)) db-name]))
                           [(hx/cast :date :started_at) :day])
               :from      [[:query_execution :qe]]
               :left-join [[:report_card :card]     [:= :qe.card_id :card.id]
                           [:metabase_table :t]     [:= :card.table_id :t.id]
                           [:metabase_database :db] [:= :t.db_id :db.id]]
               :where     [:and
                           [:not= :qe.card_id nil]
                           [:not= :card.table_id nil]
                           [:not= :card.table_id nil][:not= :db.id nil]]
               :group-by  [(hx/cast :date :started_at)]
               :order-by  [[(hx/cast :date :started_at) :asc]]}
              :identifiers identity)})

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
              [:tables        {:display_name "Tables",        :base_Type :type/Integer}]]
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
