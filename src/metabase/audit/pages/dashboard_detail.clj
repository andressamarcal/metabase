(ns metabase.audit.pages.dashboard-detail
  "Detail page for a single dashboard."
  (:require [metabase.audit.pages.common :as common]
            [metabase.audit.pages.common
             [card-and-dashboard-detail :as card-and-dash-detail]
             [cards :as cards]]
            [metabase.models.dashboard :refer [Dashboard]]
            [metabase.util.schema :as su]
            [schema.core :as s]))

;; SELECT avg(running_time)
;; FROM query_execution
;; WHERE card_id IN (SELECT card_id
;;                  FROM report_dashboardcard
;;                  WHERE dashboard_id = 18)

#_{:views (db/count 'ViewLog :model (hx/literal "dashboard"), :model_id 18)
 :edits (db/count 'Revision :model (hx/literal "Dashboard"), :model_id 18)
 :avg_load_time_ms nil}

(s/defn ^:internal-query-fn views-by-time
  "Get views of a Dashboard broken out by a time `unit`, e.g. `day` or `day-of-week`."
  [dashboard-id :- su/IntGreaterThanZero, unit :- common/DateTimeUnitStr]
  (card-and-dash-detail/views-by-time "dashboard" dashboard-id unit))

(s/defn ^:internal-query-fn revision-history
  [dashboard-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/revision-history Dashboard dashboard-id))

(s/defn ^:internal-query-fn audit-log
  [dashboard-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/audit-log "dashboard" dashboard-id))

;; WITH card AS (
;;   SELECT card.*, dc.created_at AS dashcard_created_at
;;   FROM report_dashboardcard dc
;;   JOIN report_card card
;;     ON card.id = dc.card_id
;;   WHERE dc.dashboard_id = {{dashboard-id}}
;; ),
;; avg_exec_time AS (
;;   SELECT card_id, avg(running_time) AS avg_running_time_ms
;;   FROM query_execution
;;   WHERE card_id IN (SELECT id FROM card)
;;   GROUP BY card_id
;; ),
;; card_views AS (
;;   SELECT model_id AS card_id, count(*) AS count
;;   FROM view_log
;;   WHERE model = 'card'
;;     AND model_id IN (SELECT id FROM card)
;;   GROUP BY model_id
;; )
;;
;; SELECT
;;   card.id AS card_id,
;;   card.name AS card_name,
;;   coll.id AS collection_id,
;;   coll.name AS collection_name,
;;   card.dashcard_created_at AS created_at,
;;   card.database_id,
;;   db.name AS database_name,
;;   card.table_id,
;;   t.name AS table_name,
;;   avg_exec_time.avg_running_time_ms,
;;   card.cache_ttl,
;;   (CASE WHEN card.public_uuid IS NOT NULL THEN concat({{public-url}}, card.public_uuid) END)
;;     AS public_link,
;;   card_views.count AS total_views
;; FROM card
;; LEFT JOIN avg_exec_time
;;   ON card.id = avg_exec_time.card_id
;; LEFT JOIN metabase_database db
;;   ON card.database_id = db.id
;; LEFT JOIN metabase_table t
;;   ON card.table_id = t.id
;; LEFT JOIN collection coll
;;   ON card.collection_id = coll.id
;; LEFT JOIN card_views
;;   ON card.id = card_views.card_id
;; ORDER BY lower(card.name) ASC
(s/defn ^:internal-query-fn cards
  [dashboard-id :- su/IntGreaterThanZero]
  {:metadata [[:card_id             {:display_name "Card ID",              :base_type :type/Integer, :remapped_to   :card_name}]
              [:card_name           {:display_name "Title",                :base_type :type/Name,    :remapped_from :card_id}]
              [:collection_id       {:display_name "Collection ID",        :base_type :type/Integer, :remapped_to   :collection_name}]
              [:collection_name     {:display_name "Collection",           :base_type :type/Text,    :remapped_from :collection_id}]
              [:created_at          {:display_name  "Created At",          :base_type :type/DateTime}]
              [:database_id         {:display_name "Database ID",          :base_type :type/Integer, :remapped_to   :database_name}]
              [:database_name       {:display_name "Database",             :base_type :type/Text,    :remapped_from :database_id}]
              [:table_id            {:display_name "Table ID",             :base_type :type/Integer, :remapped_to   :table_name}]
              [:table_name          {:display_name "Table",                :base_type :type/Text,    :remapped_from :table_id}]
              [:avg_running_time_ms {:display_name "Avg. exec. time (ms)", :base_type :type/Number}]
              [:cache_ttl           {:display_name "Cache TTL",            :base_type :type/Number}]
              [:public_link         {:display_name "Public Link",          :base_type :type/URL}]
              [:total_views         {:display_name "Total Views",          :base_type :type/Integer}]]
   :results  (common/query
              {:with      [[:card {:select [:card.*
                                            [:dc.created_at :dashcard_created_at]]
                                   :from   [[:report_dashboardcard :dc]]
                                   :join   [[:report_card :card] [:= :card.id :dc.card_id]]
                                   :where  [:= :dc.dashboard_id dashboard-id]}]
                           cards/avg-exec-time
                           cards/views]
               :select    [[:card.id :card_id]
                           [:card.name :card_name]
                           [:coll.id :collection_id]
                           [:coll.name :collection_name]
                           [:card.dashcard_created_at :created_at]
                           :card.database_id
                           [:db.name :database_name]
                           :card.table_id
                           [:t.name :table_name]
                           :avg_exec_time.avg_running_time_ms
                           [(common/card-public-url :card.public_uuid) :public_link]
                           :card.cache_ttl
                           [:card_views.count :total_views]]
               :from      [:card]
               :left-join [:avg_exec_time           [:= :card.id :avg_exec_time.card_id]
                           [:metabase_database :db] [:= :card.database_id :db.id]
                           [:metabase_table :t]     [:= :card.table_id :t.id]
                           [:collection :coll]      [:= :card.collection_id :coll.id]
                           :card_views              [:= :card.id :card_views.card_id]]
               :order-by  [[:%lower.card.name :asc]]})})
