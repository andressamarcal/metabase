(ns metabase.audit.pages.common.cards
  (:require [metabase.util.honeysql-extensions :as hx]))

(def avg-exec-time
  [:avg_exec_time {:select   [:card_id
                              [:%avg.running_time :avg_running_time_ms]]
                   :from     [:query_execution]
                   :group-by [:card_id]}])

(def views
  [:card_views {:select   [[:model_id :card_id]
                           [:%count.* :count]]
                :from     [:view_log]
                :where    [:= :model (hx/literal "card")]
                :group-by [:model_id]}])
