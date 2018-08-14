(ns metabase.audit.pages.user-detail
  (:require [honeysql.core :as hsql]
            [metabase.audit.pages.common :as common]
            [metabase.audit.pages.common.dashboards :as dashboards]
            [metabase.util
             [honeysql-extensions :as hx]
             [schema :as su]]
            [schema.core :as s]
            [toucan.db :as db]))

;; WITH last_query AS (
;;     SELECT max(started_at) AS started_at
;;     FROM query_execution
;;     WHERE executor_id = {{user_id}}
;; ),
;;
;; groups AS (
;;     SELECT string_agg(pg.name, ', ') AS "groups"
;;     FROM permissions_group_membership pgm
;;     LEFT JOIN permissions_group pg
;;       ON pgm.group_id = pg.id
;;     WHERE pgm.user_id = {{user_id}}
;; ),
;;
;; questions_saved AS (
;;     SELECT count(*) AS "count"
;;     FROM report_card
;;     WHERE creator_id = {{user_id}}
;; ),
;;
;; dashboards_saved AS (
;;     SELECT count(*) AS "count"
;;     FROM report_dashboard
;;     WHERE creator_id = {{user_id}}
;; ),
;;
;; pulses_saved AS (
;;     SELECT count(*) AS "count"
;;     FROM pulse
;;     WHERE creator_id = {{user_id}}
;; ),
;;
;; users AS (
;;     SELECT
;;       (u.first_name || ' ' || u.last_name) AS "name",
;;       (CASE WHEN u.is_superuser THEN 'Admin' ELSE 'User' END) AS "role",
;;       date_joined,
;;       (CASE WHEN u.sso_source IS NULL THEN 'Email' ELSE u.sso_source END) AS signup_method
;;     FROM core_user u
;;     WHERE u.id = {{user_id}}
;; )
;;
;; SELECT
;;   u."name",
;;   u."role",
;;   groups.groups AS groups,
;;   u.date_joined,
;;   last_query.started_at AS last_active,
;;   u.signup_method,
;;   questions_saved.count AS questions_saved,
;;   dashboards_saved.count AS dashboards_saved,
;;   pulses_saved.count AS pulses_saved
;; FROM users u, groups, last_query, questions_saved, dashboards_saved, pulses_saved
(s/defn ^:internal-query-fn table
  "Query that probides a single row of information about a given User, similar to the `users/table` query but restricted
  to a single result.
  (TODO - in the designs, this is pivoted; should we do that here in Clojure-land?)"
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:name             {:display_name "Name",             :base_type :type/Name}]
              [:role             {:display_name "Role",             :base_type :type/Text}]
              [:groups           {:display_name "Groups",           :base_type :type/Text}]
              [:date_joined      {:display_name "Date Joined",      :base_type :type/DateTime}]
              [:last_active      {:display_name "Last Active",      :base_type :type/DateTime}]
              [:signup_method    {:display_name "Signup Method",    :base_type :type/Text}]
              [:questions_saved  {:display_name "Questions Saved",  :base_type :type/Integer}]
              [:dashboards_saved {:display_name "Dashboards Saved", :base_type :type/Integer}]
              [:pulses_saved     {:display_name "Pulses Saved",     :base_type :type/Integer}]]
   :results  (db/query
              {:with   [[:last_query {:select [[:%max.started_at :started_at]]
                                      :from   [:query_execution]
                                      :where  [:= :executor_id user-id]}]
                        [:groups {:select    [[(hsql/call :string_agg :pg.name (hx/literal ", ")) :groups]]
                                  :from      [[:permissions_group_membership :pgm]]
                                  :left-join [[:permissions_group :pg] [:= :pgm.group_id :pg.id]]
                                  :where     [:= :pgm.user_id user-id]}]
                        [:questions_saved {:select [[:%count.* :count]]
                                           :from   [:report_card]
                                           :where  [:= :creator_id user-id]}]
                        [:dashboards_saved {:select [[:%count.* :count]]
                                            :from   [:report_dashboard]
                                            :where  [:= :creator_id user-id]}]
                        [:pulses_saved {:select [[:%count.* :count]]
                                        :from   [:pulse]
                                        :where  [:= :creator_id user-id]}]
                        [:users {:select [[(common/user-full-name :u) :name]
                                          [(hsql/call :case
                                             [:= :u.is_superuser true]
                                             (hx/literal "Admin")
                                             :else
                                             (hx/literal "User"))
                                           :role]
                                          :id
                                          :date_joined
                                          [(hsql/call :case
                                             [:= nil :u.sso_source]
                                             (hx/literal "Email")
                                             :else
                                             :u.sso_source)
                                           :signup_method]
                                          :last_name]
                                 :from   [[:core_user :u]]
                                 :where  [:= :u.id user-id]}]]
               :select [:u.name
                        :u.role
                        :groups.groups
                        :u.date_joined
                        [:last_query.started_at :last_active]
                        :u.signup_method
                        [:questions_saved.count :questions_saved]
                        [:dashboards_saved.count :dashboards_saved]
                        [:pulses_saved.count :pulses_saved]]
               :from   [[:users :u]
                        :groups
                        :last_query
                        :questions_saved
                        :dashboards_saved
                        :pulses_saved]})})

(s/defn ^:internal-query-fn most-viewed-dashboards
  "Return the 10 most-viewed Dashboards for a given User, in descending order."
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:dashboard_id   {:display_name "Dashboard ID", :base_type :type/Integer, :remapped_to   :dashboard_name}]
              [:dashboard_name {:display_name "Dashboard",    :base_type :type/Name,    :remapped_from :dashboard_id}]
              [:count          {:display_name "Views",        :base_type :type/Integer}]]
   :results  (db/query {:select    [[:d.id :dashboard_id]
                                    [:d.name :dashboard_name]
                                    [:%count.* :count]]
                        :from      [[:view_log :vl]]
                        :left-join [[:report_dashboard :d] [:= :vl.model_id :d.id]]
                        :where     [:and
                                    [:= :vl.user_id user-id]
                                    [:= :vl.model (hx/literal "dashboard")]]
                        :group-by  [:d.id]
                        :order-by  [[:%count.* :desc]]
                        :limit     10})})

(s/defn ^:internal-query-fn most-viewed-questions
  "Return the 10 most-viewed Questions for a given User, in descending order."
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:card_id   {:display_name "Card ID", :base_type :type/Integer, :remapped_to   :card_name}]
              [:card_name {:display_name "Card",    :base_type :type/Name,    :remapped_from :card_id}]
              [:count     {:display_name "Views",   :base_type :type/Integer}]]
   :results  (db/query {:select    [[:d.id :card_id]
                                    [:d.name :card_name]
                                    [:%count.* :count]]
                        :from      [[:view_log :vl]]
                        :left-join [[:report_card :d] [:= :vl.model_id :d.id]]
                        :where     [:and
                                    [:= :vl.user_id user-id]
                                    [:= :vl.model (hx/literal "card")]]
                        :group-by  [:d.id]
                        :order-by  [[:%count.* :desc]]
                        :limit     10})})

(s/defn ^:internal-query-fn query-views
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:viewed_on     {:display_name "Viewed On",      :base_type :type/DateTime}]
              [:type          {:display_name "Type",           :base_type :type/Text}]
              [:collection_id {:display_name "Collection ID",  :base_type :type/Integer, :remapped_to   :collection}]
              [:collection    {:display_name "Collection",     :base_type :type/Text,    :remapped_from :collection_id}]
              [:saved_by_id   {:display_name "Saving User ID", :base_type :type/Integer, :remapped_to   :saved_by}]
              [:saved_by      {:display_name "Saved By",       :base_type :type/Text,    :remapped_from :saved_by_id}]
              [:database_id   {:display_name "Database ID",    :base_type :type/Integer, :remapped_to   :source_db}]
              [:source_db     {:display_name "Source DB",      :base_type :type/Text,    :remapped_from :database_id}]
              [:table_id      {:display_name "Table ID"        :base_type :type/Integer, :remapped_to   :table}]
              [:table         {:display_name "Table",          :base_type :type/Text,    :remapped_from :table_id}]]
   :results (db/query
             {:select    [[:qe.started_at :viewed_on]
                          [(hsql/call :case [:= :qe.native true] (hx/literal "Native") :else (hx/literal "GUI")) :type]
                          [:collection.id :collection_id]
                          [:collection.name :collection]
                          [:u.id :saved_by_id]
                          [(common/user-full-name :u) :saved_by]
                          [:db.id :database_id]
                          [:db.name :source_db]
                          [:t.id :table_id]
                          [:t.display_name :table]]
              :from      [[:query_execution :qe]]
              :join      [[:metabase_database :db] [:= :qe.database_id :db.id]]
              :left-join [[:report_card :card]     [:= :qe.card_id :card.id]
                          :collection              [:= :card.collection_id :collection.id]
                          [:core_user :u]          [:= :card.creator_id :u.id]
                          [:metabase_table :t]     [:= :card.table_id :t.id]]
              :where     [:= :qe.executor_id user-id]
              :order-by  [[:qe.started_at :desc]]})})

(s/defn ^:internal-query-fn dashboard-views
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:timestamp       {:display_name "Viewed on",     :base_type :type/DateTime}]
              [:dashboard_id    {:display_name "Dashboard ID",  :base_type :type/Integer, :remapped_to   :dashboard_name}]
              [:dashboard_name  {:display_name "Dashboard",     :base_type :type/Text,    :remapped_from :dashboard_id}]
              [:collection_id   {:display_name "Collection ID", :base_type :type/Integer, :remapped_to   :collection_name}]
              [:collection_name {:display_name "Collection",    :base_type :type/Text,    :remapped_from :collection_id}]]
   :results (db/query
             {:select    [:vl.timestamp
                          [:dash.id :dashboard_id]
                          [:dash.name :dashboard_name]
                          [:coll.id :collection_id]
                          [:coll.name :collection_name]]
              :from      [[:view_log :vl]]
              :where     [:and
                          [:= :vl.model (hx/literal "dashboard")]
                          [:= :vl.user_id user-id]]
              :join      [[:report_dashboard :dash] [:= :vl.model_id :dash.id]]
              :left-join [[:collection :coll] [:= :dash.collection_id :coll.id]]
              :order-by  [[:vl.timestamp :desc]]})})

(s/defn ^:internal-query-fn object-views-by-time
  "Timeseries chart that shows the number of Question or Dashboard views for a User, broken out by `datetime-unit`."
  [user-id :- su/IntGreaterThanZero, model :- (s/enum "card" "dashboard"), datetime-unit :- common/DateTimeUnitStr]
  {:metadata [[:date {:display_name "Date",   :base_type (common/datetime-unit-str->base-type datetime-unit)}]
              [:views {:display_name "Views", :base_type :type/Integer}]]
   :results (db/query
             {:select   [[(common/grouped-datetime datetime-unit :timestamp) :date]
                         [:%count.* :views]]
              :from     [:view_log]
              :where    [:and
                         [:= :user_id user-id]
                         [:= :model model]]
              :group-by [(common/grouped-datetime datetime-unit :timestamp)]
              :order-by [[(common/grouped-datetime datetime-unit :timestamp) :asc]]})})

(s/defn ^:internal-query-fn created-dashboards
  [user-id :- su/IntGreaterThanZero]
  (dashboards/table [:= :u.id user-id]))
