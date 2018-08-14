(ns metabase.audit.pages.common.card-and-dashboard-detail
  "Common queries used by both Card (Question) and Dashboard detail pages."
  (:require [metabase.audit.pages.common :as audit-common]
            [metabase.models
             [card :refer [Card]]
             [dashboard :refer [Dashboard]]
             [revision :as revision]]
            [metabase.util
             [honeysql-extensions :as hx]
             [schema :as su]]
            [schema.core :as s]
            [toucan.db :as db]))

(def ^:private ModelName
  (s/enum "card" "dashboard"))

;; SELECT {{group-fn(timestamp}} AS "date", count(*) AS views
;; FROM view_log
;; WHERE model = {{model}}
;;   AND model_id = {{model-id}}
;; GROUP BY {{group-fn(timestamp}}
;; ORDER BY {{group-fn(timestamp}} ASC
(s/defn views-by-time
  "Get views of a Card or Dashboard broken out by a time `unit`, e.g. `day` or `day-of-week`."
  [model :- ModelName, model-id :- su/IntGreaterThanZero, unit :- audit-common/DateTimeUnitStr]
  {:metadata [[:date  {:display_name "Date",  :base_type (audit-common/datetime-unit-str->base-type unit)}]
              [:views {:display_name "Views", :base_type :type/Integer}]]
   :results (let [grouped-timestamp (audit-common/grouped-datetime unit :timestamp)]
              (db/query
               {:select   [[grouped-timestamp :date]
                           [:%count.* :views]]
                :from     [:view_log]
                :where    [:and
                           [:= :model (hx/literal model)]
                           [:= :model_id model-id]]
                :group-by [grouped-timestamp]
                :order-by [[grouped-timestamp :asc]]}))})

(s/defn revision-history
  "Get a revision history table for a Card or Dashboard."
  [model-entity :- (s/cond-pre (class Card) (class Dashboard)), model-id :- su/IntGreaterThanZero]
  {:metadata [[:timestamp   {:display_name "Edited on",   :base_type :type/DateTime}]
              [:user_id     {:display_name "User ID",     :base_type :type/Integer, :remapped_to   :user_name}]
              [:user_name   {:display_name "Edited by",   :base_type :type/Name,    :remapped_from :user_id}]
              [:change_made {:display_name "Change made", :base_type :type/Text}]
              [:revision_id {:display_name "Revision ID", :base_type :type/Integer}]]
   :results (for [revision (revision/revisions+details model-entity model-id)]
              {:timestamp   (-> revision :timestamp)
               :user_id     (-> revision :user :id)
               :user_name   (-> revision :user :common_name)
               :change_made (-> revision :description)
               :revision_id (-> revision :id)})})

;; WITH views AS (
;;  SELECT CAST(timestamp AS DATE) AS day, user_id
;;  FROM view_log
;;  WHERE model = {{model}}
;;    AND model_id = {{model-id}}
;;  GROUP BY CAST(timestamp AS DATE), user_id
;; )
;;
;; SELECT v.day AS when, v.user_id, (u.first_name || ' ' || u.last_name) AS who
;; FROM views v
;; LEFT JOIN core_user u
;;   ON v.user_id = u.id
;; ORDER BY v.day DESC, lower(u.last_name) ASC, lower(u.first_name) ASC
(s/defn audit-log
  [model :- ModelName, model-id :- su/IntGreaterThanZero]
  {:metadata [[:when    {:display_name "When",    :base_type :type/Date}]
              [:user_id {:display_name "User ID", :base_type :type/Integer, :remapped_to   :who}]
              [:who     {:display_name "Who",     :base_type :type/Name,    :remapped_from :user_id}]]
   :results (db/query
             {:with      [[:views {:select   [[(hx/cast :date :timestamp) :day]
                                              :user_id]
                                   :from     [:view_log]
                                   :where    [:and
                                              [:= :model (hx/literal model)]
                                              [:= :model_id model-id]]
                                   :group-by [(hx/cast :date :timestamp) :user_id]}]]
              :select    [[:v.day :when]
                          :v.user_id
                          [(audit-common/user-full-name :u) :who]]
              :from      [[:views :v]]
              :left-join [[:core_user :u] [:= :v.user_id :u.id]]
              :order-by  [[:v.day :desc]
                          [:%lower.u.last_name :asc]
                          [:%lower.u.first_name :asc]]})})
