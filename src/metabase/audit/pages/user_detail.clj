(ns metabase.audit.pages.user-detail
  (:require [honeysql.core :as hsql]
            [metabase.audit.pages.common :as audit-common]
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
  {:metadata [[:name             {:display_name "Name", :base_type :type/Name}]
              [:role             {:display_name "Role", :base_type :type/Text}]
              [:groups           {:display_name "Groups", :base_type :type/Text}]
              [:date_joined      {:display_name "Date Joined", :base_type :type/DateTime}]
              [:last_active      {:display_name "Last Active", :base_type :type/DateTime}]
              [:signup_method    {:display_name "Signup Method", :base_type :type/Text}]
              [:questions_saved  {:display_name "Questions Saved", :base_type :type/Integer}]
              [:dashboards_saved {:display_name "Dashboards Saved", :base_type :type/Integer}]
              [:pulses_saved     {:display_name "Pulses Saved", :base_type :type/Integer}]]
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
                        [:users {:select [[(audit-common/user-full-name :u) :name]
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

;; SELECT
;;   qe.started_at AS viewed_on,
;;   card.name AS query,
;;   (CASE
;;       WHEN card.query_type = 'query' THEN 'GUI'
;;       WHEN card.query_type = 'native' THEN 'Native'
;;       ELSE card.query_type
;;   END) AS type,
;;   collection.name AS collection,
;;   (u.first_name || ' ' || u.last_name) AS saved_by,
;;   db.name AS source_db,
;;   t.display_name AS table
;; FROM query_execution qe
;; LEFT JOIN report_card card
;;   ON qe.card_id = card.id
;; LEFT JOIN collection
;;   ON card.collection_id = collection.id
;; LEFT JOIN core_user u
;;   ON card.creator_id = u.id
;; LEFT JOIN metabase_table t
;;   ON card.table_id = t.id
;; LEFT JOIN metabase_database db
;;   ON t.db_id = db.id
;; WHERE qe.executor_id = {{user_id}}
;;   AND qe.card_id IS NOT NULL
;; ORDER BY qe.started_at DESC
(s/defn ^:internal-query-fn query-views
  [user-id :- su/IntGreaterThanZero]
  {:metadata [[:viewed_on  {:display_name "Viewed On",  :base_type :type/DateTime}]
              [:type       {:display_name "Type",       :base_type :type/Text}]
              [:collection {:display_name "Collection", :base_type :type/Text}]
              [:saved_by   {:display_name "Saved By",   :base_type :type/Text}]
              [:source_db  {:display_name "Source DB",  :base_type :type/Text}]
              [:table      {:display_name "Table",      :base_type :type/Text}]]
   :results  (db/query
              {:select    [[:qe.started_at :viewed_on]
                           [(hsql/call :case
                              [:= :card.query_type (hx/literal "query")]  (hx/literal "GUI")
                              [:= :card.query_type (hx/literal "native")] (hx/literal "Native")
                              :else                                       :card.query_type)
                            :type]
                           [:collection.name :collection]
                           [(hx/concat :u.first_name (hx/literal " ") :u.last_name) :saved_by]
                           [:db.name :source_db]
                           [:t.display_name :table]]
               :from      [[:query_execution :qe]]
               :left-join [[:report_card :card]     [:= :qe.card_id :card.id]
                           :collection              [:= :card.collection_id :collection.id]
                           [:core_user :u]          [:= :card.creator_id :u.id]
                           [:metabase_table :t]     [:= :card.table_id :t.id]
                           [:metabase_database :db] [:= :t.db_id :db.id]]
               :where     [:and
                           [:= :qe.executor_id user-id]
                           [:not= :qe.card_id nil]]
               :order-by  [[:qe.started_at :desc]]})})
