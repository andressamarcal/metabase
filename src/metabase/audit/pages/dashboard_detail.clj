(ns metabase.audit.pages.dashboard-detail
  (:require [honeysql.core :as hsql]
            [metabase.audit.pages.common :as audit-common]
            [metabase.models.dashboard :refer [Dashboard]]
            [metabase.models.revision :as revision]
            [metabase.util
             [honeysql-extensions :as hx]
             [schema :as su]]
            [schema.core :as s]
            [toucan.db :as db]))

(s/defn ^:internal-query-fn revision-history
  [dashboard :- su/IntGreaterThanZero]
  {:metadata [[:timestamp   {:display_name "Edited on",   :base_type :type/DateTime}]
              [:user_id     {:display_name "User ID",     :base_type :type/Integer, :remapped_to   :user_name}]
              [:user_name   {:display_name "Edited by",   :base_type :type/Name,    :remapped_from :user_id}]
              [:change_made {:display_name "Change made", :base_type :type/Text}]
              [:revision_id {:display_name "Revision ID", :base_type :type/Integer}]]
   :results (for [revision (revision/revisions+details Dashboard 18)]
              {:timestamp   (-> revision :timestamp)
               :user_id     (-> revision :user :id)
               :user_name   (-> revision :user :common_name)
               :change_made (-> revision :description)
               :revision_id (-> revision :id)})})

;; WITH views AS (
;;  SELECT DISTINCT CAST(timestamp AS DATE) AS day, user_id
;;  FROM view_log
;;  WHERE model = 'dashboard'
;;    AND model_id = {{dashboard-id}}
;; )
;;
;; SELECT v.day AS when, v.user_id, (u.first_name || ' ' || u.last_name) AS who
;; FROM views v
;; LEFT JOIN core_user u
;;   ON v.user_id = u.id
;; ORDER BY v.day DESC, lower(u.last_name) ASC, lower(u.first_name) ASC
(s/defn ^:internal-query-fn audit-log
  [dashboard-id :- su/IntGreaterThanZero]
  {:metadata [[:when    {:display_name "When",    :base_type :type/DateTime}]
              [:user_id {:display_name "User ID", :base_type :type/Integer, :remapped_to   :who}]
              [:who     {:display_name "Who",     :base_type :type/Name,    :remapped_from :user_id}]]
   :results (db/query
             {:with      [[:views {:select   [[(hx/cast :date :timestamp) :day]
                                              :user_id]
                                   :from     [:view_log]
                                   :where    [:and
                                              [:= :model (hx/literal "dashboard")]
                                              [:= :model_id dashboard-id]]
                                   :group-by [(hx/cast :date :timestamp) :user_id]}]]
              :select    [[:v.day :when]
                          :v.user_id
                          [(audit-common/user-full-name :u) :who]]
              :from      [[:views :v]]
              :left-join [[:core_user :u] [:= :v.user_id :u.id]]
              :order-by  [[:v.day :desc]
                          [:%lower.u.last_name :asc]
                          [:%lower.u.first_name :asc]]})})
