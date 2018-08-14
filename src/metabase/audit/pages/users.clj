(ns metabase.audit.pages.users
  (:require [honeysql.core :as hsql]
            [metabase.audit.pages.common :as audit-common]
            [metabase.util.honeysql-extensions :as hx]
            [toucan.db :as db]
            [schema.core :as s]))

;; WITH user_qe AS (
;;     SELECT executor_id, count(*) AS executions, CAST(started_at AS DATE) AS day
;;     FROM query_execution
;;     GROUP BY executor_id, day
;; )
;;
;; SELECT
;;   count(*) AS "Users",
;;   sum(executions) AS "Queries",
;;   day
;; FROM user_qe
;; GROUP BY day
;; ORDER BY day ASC
(defn ^:internal-query-fn ^:deprecated active-users-and-queries-by-day
  "Query that returns data for a two-series timeseries: the number of DAU (a User is considered active for purposes of
  this query if they ran at least one query that day), and total number of queries ran. Broken out by day."
  []
  {:metadata [[:users   {:display_name "Users",   :base_type :type/Integer}]
              [:queries {:display_name "Queries", :base_type :type/Integer}]
              [:day     {:display_name "Date",    :base_type :type/Date}]]
   :results  (db/query
              {:with     [[:user_qe {:select   [:executor_id
                                                [:%count.* :executions]
                                                [(hx/cast :date :started_at) :day]]
                                     :from     [:query_execution]
                                     :group-by [:executor_id :day]}]]
               :select   [[:%count.* :users]
                          [:%sum.executions :queries]
                          :day]
               :from     [:user_qe]
               :group-by [:day]
               :order-by [[:day :asc]]})})

(s/defn ^:internal-query-fn active-and-new-by-time
  "Two-series timeseries that returns number of active Users (Users who ran at least one query) and number of new Users,
  broken out by `datetime-unit`."
  [datetime-unit :- audit-common/DateTimeUnit]
  {:metadata [[:date         {:display_name "Date",         :base_type (audit-common/datetime-unit-str->base-type datetime-unit)}]
              [:active_users {:display_name "Active Users", :base_type :type/Integer}]
              [:new_users    {:display_name "New Users",    :base_type :type/Integer}]]
   :results  (db/query
              {:with      [[:active {:select   [[(audit-common/grouped-datetime datetime-unit :started_at) :date]
                                                [:%distinct-count.executor_id :count]]
                                     :from     [:query_execution]
                                     :group-by [(audit-common/grouped-datetime datetime-unit :started_at)]}]
                           [:new  {:select   [[(audit-common/grouped-datetime datetime-unit :date_joined) :date]
                                              [:%count.* :count]]
                                   :from     [:core_user]
                                   :group-by [(audit-common/grouped-datetime datetime-unit :date_joined)]}]]
               :select    [[(hsql/call :case [:not= :active.date nil] :active.date :else :new.date) :date]
                           [(hsql/call :case [:not= :active.count nil] :active.count :else 0) :active_users]
                           [(hsql/call :case [:not= :new.count nil] :new.count :else 0) :new_users]]
               :from      [:active]
               :full-join [:new [:= :active.date :new.date]]
               :order-by  [[:date :asc]]})})

;; WITH qe_count AS (
;;   SELECT count(*) AS "count", qe.executor_id
;;   FROM query_execution qe
;;   WHERE qe.executor_id IS NOT NULL
;;   GROUP BY qe.executor_id
;;   ORDER BY count(*) DESC
;;   LIMIT 10
;; )
;;
;; SELECT
;;   u.id AS user_id,
;;   (u.first_name || ' ' || u.last_name) AS "name",
;;   CASE(WHEN qe_count."count" IS NOT NULL THEN qe_count."count" ELSE 0) AS "count"
;; FROM core_user u
;; LEFT JOIN qe_count
;;   ON qe_count.executor_id = u.id
;; ORDER BY count DESC, lower(u.last_name) ASC, lower(u.first_name ASC)
;; LIMIT 10
(defn ^:internal-query-fn most-active
  "Query that returns the 10 most active Users (by number of query executions) in descending order."
  []
  {:metadata [[:user_id {:display_name "User ID",          :base_type :type/Integer, :remapped_to   :name}]
              [:name    {:display_name "Name",             :base_type :type/Name,    :remapped_from :user_id}]
              [:count   {:display_name "Query Executions", :base_type :type/Integer}]]
   :results  (db/query
              {:with      [[:qe_count {:select   [[:%count.* :count]
                                                  :qe.executor_id]
                                       :from     [[:query_execution :qe]]
                                       :where    [:not= nil :qe.executor_id]
                                       :group-by [:qe.executor_id]
                                       :order-by [[:%count.* :desc]]
                                       :limit    10}]]
               :select    [[:u.id :user_id]
                           [(audit-common/user-full-name :u) :name]
                           [(hsql/call :case [:not= :qe_count.count nil] :qe_count.count :else 0) :count]]
               :from      [[:core_user :u]]
               :left-join [:qe_count [:= :qe_count.executor_id :u.id]]
               :order-by  [[:count :desc]
                           [:%lower.u.last_name :asc]
                           [:%lower.u.first_name :asc]]
               :limit     10})})

;; WITH exec_time AS (
;;   SELECT sum(running_time) AS execution_time_ms, qe.executor_id
;;   FROM query_execution qe
;;   WHERE qe.executor_id IS NOT NULL
;;   GROUP BY qe.executor_id
;;   ORDER BY sum(running_time) DESC
;;   LIMIT 10
;; )
;;
;; SELECT
;;   u.id AS user_id,
;;   (u.first_name || ' ' || u.last_name) AS "name",
;;   CASE (WHEN exec_time.execution_time_ms IS NOT NULL THEN exec_time.execution_time_ms ELSE 0) AS execution_time_ms
;; FROM core_user u
;; LEFT JOIN exec_time
;;   ON exec_time.executor_id = u.id
;; ORDER BY execution_time_ms DESC, lower(u.last_name) ASC, lower(u.first_name) ASC
;; LIMIT 10
(defn ^:internal-query-fn query-execution-time-per-user
  "Query that returns the total time spent executing queries, broken out by User, for the top 10 Users."
  []
  {:metadata [[:user_id           {:display_name "User ID",                   :base_type :type/Integer, :remapped_to   :name}]
              [:name              {:display_name "Name",                      :base_type :type/Name,    :remapped_from :user_id}]
              [:execution_time_ms {:display_name "Total Execution Time (ms)", :base_type :type/Decimal}]]
   :results  (db/query
              {:with      [[:exec_time {:select   [[:%sum.running_time :execution_time_ms]
                                                   :qe.executor_id]
                                        :from     [[:query_execution :qe]]
                                        :where    [:not= nil :qe.executor_id]
                                        :group-by [:qe.executor_id]
                                        :order-by [[:%sum.running_time :desc]]
                                        :limit    10}]]
               :select    [[:u.id :user_id]
                           [(audit-common/user-full-name :u) :name]
                           [(hsql/call :case [:not= :exec_time.execution_time_ms nil] :exec_time.execution_time_ms
                                       :else 0)
                            :execution_time_ms]]
               :from      [[:core_user :u]]
               :left-join [:exec_time [:= :exec_time.executor_id :u.id]]
               :order-by  [[:execution_time_ms :desc]
                           [:%lower.u.last_name :asc]
                           [:%lower.u.first_name :asc]]
               :limit     10})})

;; WITH last_query AS (
;;     SELECT executor_id AS "id", max(started_at) AS started_at
;;     FROM query_execution
;;     GROUP BY executor_id
;; ),
;;
;; groups AS (
;;     SELECT u.id AS id, string_agg(pg.name, ', ') AS "groups"
;;     FROM core_user u
;;     LEFT JOIN permissions_group_membership pgm
;;       ON u.id = pgm.user_id
;;     LEFT JOIN permissions_group pg
;;       ON pgm.group_id = pg.id
;;     GROUP BY u.id
;; ),
;;
;; questions_saved AS (
;;     SELECT u.id AS id, count(*) AS "count"
;;     FROM report_card c
;;     LEFT JOIN core_user u
;;       ON u.id = c.creator_id
;;     GROUP BY u.id
;; ),
;;
;; dashboards_saved AS (
;;     SELECT u.id AS id, count(*) AS "count"
;;     FROM report_dashboard d
;;     LEFT JOIN core_user u
;;       ON u.id = d.creator_id
;;     GROUP BY u.id
;; ),
;;
;; pulses_saved AS (
;;     SELECT u.id AS id, count(*) AS "count"
;;     FROM pulse p
;;     LEFT JOIN core_user u
;;       ON u.id = p.creator_id
;;     GROUP BY u.id
;; ),
;;
;; users AS (
;;     SELECT (u.first_name || ' ' || u.last_name) AS "name",
;;       (CASE WHEN u.is_superuser THEN 'Admin' ELSE 'User' END) AS "role",
;;       id,
;;       date_joined,
;;       (CASE WHEN u.sso_source IS NULL THEN 'Email' ELSE u.sso_source END) AS signup_method,
;;       last_name,
;;       first_name
;;     FROM core_user u
;; )
;;
;; SELECT
;;   u.id AS user_id,
;;   u."name",
;;   u."role",
;;   groups.groups AS groups,
;;   u.date_joined,
;;   last_query.started_at AS last_active,
;;   u.signup_method
;;   questions_saved.count AS questions_saved,
;;   dashboards_saved.count AS dashboards_saved,
;;   pulses_saved.count AS pulses_saved
;; FROM users u
;; LEFT JOIN groups           ON u.id = groups.id
;; LEFT JOIN last_query       ON u.id = last_query.id
;; LEFT JOIN questions_saved  ON u.id = questions_saved.id
;; LEFT JOIN dashboards_saved ON u.id = dashboards_saved.id
;; LEFT JOIN pulses_saved     ON u.id = pulses_saved.id
;; ORDER BY lower(u.last_name) ASC, lower(u.first_name) ASC
(defn ^:internal-query-fn table []
  {:metadata [[:user_id          {:display_name "User ID",          :base_type :type/Integer, :remapped_to   :name}]
              [:name             {:display_name "Name",             :base_type :type/Name,    :remapped_from :user_id}]
              [:role             {:display_name "Role",             :base_type :type/Text}]
              [:groups           {:display_name "Groups",           :base_type :type/Text}]
              [:date_joined      {:display_name "Date Joined",      :base_type :type/DateTime}]
              [:last_active      {:display_name "Last Active",      :base_type :type/DateTime}]
              [:signup_method    {:display_name "Signup Method",    :base_type :type/Text}]
              [:questions_saved  {:display_name "Questions Saved",  :base_type :type/Integer}]
              [:dashboards_saved {:display_name "Dashboards Saved", :base_type :type/Integer}]
              [:pulses_saved     {:display_name "Pulses Saved",     :base_type :type/Integer}]]
   :results (db/query
             {:with      [[:last_query {:select   [[:executor_id :id]
                                                   [:%max.started_at :started_at]]
                                        :from     [:query_execution]
                                        :group-by [:executor_id]}]
                          [:groups {:select    [[:u.id :id]
                                                [(hsql/call :string_agg :pg.name (hx/literal ", ")) :groups]]
                                    :from      [[:core_user :u]]
                                    :left-join [[:permissions_group_membership :pgm] [:= :u.id :pgm.user_id]
                                                [:permissions_group :pg]             [:= :pgm.group_id :pg.id]]
                                    :group-by  [:u.id]}]
                          [:questions_saved {:select    [[:u.id :id]
                                                         [:%count.* :count]]
                                             :from      [[:report_card :c]]
                                             :left-join [[:core_user :u] [:= :u.id :c.creator_id]]
                                             :group-by  [:u.id]}]
                          [:dashboards_saved {:select    [[:u.id :id]
                                                          [:%count.* :count]]
                                              :from      [[:report_dashboard :d]]
                                              :left-join [[:core_user :u] [:= :u.id :d.creator_id]]
                                              :group-by  [:u.id]}]
                          [:pulses_saved {:select    [[:u.id :id]
                                                      [:%count.* :count]]
                                          :from      [[:pulse :p]]
                                          :left-join [[:core_user :u] [:= :u.id :p.creator_id]]
                                          :group-by  [:u.id]}]
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
                                            :last_name
                                            :first_name]
                                   :from   [[:core_user :u]]}]]
              :select    [[:u.id :user_id]
                          :u.name
                          :u.role
                          :groups.groups
                          :u.date_joined
                          [:last_query.started_at :last_active]
                          :u.signup_method
                          [:questions_saved.count :questions_saved]
                          [:dashboards_saved.count :dashboards_saved]
                          [:pulses_saved.count :pulses_saved]]
              :from      [[:users :u]]
              :left-join [:groups           [:= :u.id :groups.id]
                          :last_query       [:= :u.id :last_query.id]
                          :questions_saved  [:= :u.id :questions_saved.id]
                          :dashboards_saved [:= :u.id :dashboards_saved.id]
                          :pulses_saved     [:= :u.id :pulses_saved.id]]
              :order-by  [[:%lower.u.last_name :asc]
                          [:%lower.u.first_name :asc]]})})
