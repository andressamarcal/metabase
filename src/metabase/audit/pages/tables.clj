(ns metabase.audit.pages.tables
  (:require [metabase.util.honeysql-extensions :as hx]
            [toucan.db :as db]))

;; WITH qe_with_table AS (
;;     SELECT db."name" AS db_name, t."schema" AS db_schema, t.name AS table_name
;;     FROM query_execution qe
;;     LEFT JOIN report_card card
;;       ON qe.card_id = card.id
;;     LEFT JOIN metabase_database db
;;       ON card.database_id = db.id
;;     LEFT JOIN metabase_table t
;;       ON card.table_id = t.id
;;     WHERE qe.card_id IS NOT NULL
;;       AND card.database_id IS NOT NULL
;;       AND card.table_id IS NOT NULL
;; )
;;
;; SELECT (db_name || ' ' || db_schema || ' ' || table_name) AS "table", count(*) AS executions
;; FROM qe_with_table
;; GROUP BY db_name, db_schema, table_name
;; ORDER BY count(*) {{asc-or-desc}}
;; LIMIT 10
(defn- query-counts [asc-or-desc]
  {:metadata [[:table_name {:display_name "Table",      :base_type :type/Title}]
              [:executions {:display_name "Executions", :base_type :type/Integer}]]
   :results  (db/query
              {:with     [[:qe_with_table {:select    [[:db.name :db_name]
                                                       [:t.schema :db_schema]
                                                       [:t.name :table_name]]
                                           :from      [[:query_execution :qe]]
                                           :left-join [[:report_card :card]     [:= :qe.card_id :card.id]
                                                       [:metabase_database :db] [:= :card.database_id :db.id]
                                                       [:metabase_table :t]     [:= :card.table_id :t.id]]
                                           :where     [:and
                                                       [:not= nil :qe.card_id]
                                                       [:not= nil :card.database_id]
                                                       [:not= nil :card.table_id]]}]]
               :select   [[(hx/concat :db_name (hx/literal " ") :db_schema (hx/literal " ") :table_name) :table_name]
                          [:%count.* :executions]]
               :from     [:qe_with_table]
               :group-by [:db_name :db_schema :table_name]
               :order-by [[:%count.* asc-or-desc]]
               :limit    10})})

(defn ^:internal-query-fn most-queried
  "Query that returns the top-10 most-queried Tables, in descending order."
  []
  (query-counts :desc))

(defn ^:internal-query-fn least-queried
  "Query that returns the top-10 least-queried Tables (with at least one query execution), in ascending order."
  []
  (query-counts :asc))
