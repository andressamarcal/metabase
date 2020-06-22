(ns metabase-enterprise.audit.pages.common
  "Shared functions used by audit internal queries across different namespaces."
  (:require [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [honeysql
             [core :as hsql]
             [helpers :as h]]
            [metabase-enterprise.audit.query-processor.middleware.handle-audit-queries :as qp.middleware.audit]
            [metabase.db :as mdb]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.query-processor.context :as context]
            [metabase.util
             [honeysql-extensions :as hx]
             [urls :as urls]]
            [schema.core :as s]
            [toucan.db :as db]))

(def ^:private ^:const default-limit 1000)

(defn- add-default-params [honeysql-query]
  (let [{:keys [limit offset]} qp.middleware.audit/*additional-query-params*]
    (-> honeysql-query
        (update :limit (fn [query-limit]
                         (or limit query-limit default-limit)))
        (update :offset (fn [query-offset]
                          (or offset query-offset 0))))))

(defn- reduce-results* [honeysql-query context rff init]
  (let [driver         (mdb/db-type)
        [sql & params] (db/honeysql->sql (add-default-params honeysql-query))
        canceled-chan  (context/canceled-chan context)]
    (try
      (with-open [conn (jdbc/get-connection (db/connection))
                  stmt (sql-jdbc.execute/prepared-statement driver conn sql params)
                  rs   (sql-jdbc.execute/execute-query! driver stmt)]
        (let [rsmeta   (.getMetaData rs)
              cols     (sql-jdbc.execute/column-metadata driver rsmeta)
              metadata {:cols cols}
              rf       (rff metadata)]
          (reduce rf init (sql-jdbc.execute/reducible-rows driver rs rsmeta canceled-chan))))
      (catch InterruptedException e
        (a/>!! canceled-chan :cancel)
        (throw e)))))

(defn reducible-query
  "Return a function with the signature

    (f context) -> IReduceInit

  that, when reduced, runs `honeysql-query` against the application DB, automatically including limits and offsets for
  paging."
  [honeysql-query]
  (bound-fn reducible-query-fn [context]
    (reify clojure.lang.IReduceInit
      (reduce [_ rf init]
        (reduce-results* honeysql-query context (constantly rf) init)))))

(defn query
  "Run a internal audit query, automatically including limits and offsets for paging. This function returns results
  directly as a series of maps (the 'legacy results' format as described in
  `metabase-enterprise.audit.query-processor.middleware.handle-audit-queries.internal-queries`)"
  [honeysql-query]
  (let [context {:canceled-chan (a/promise-chan)}
        rff     (fn [{:keys [cols]}]
                  (let [col-names (mapv (comp keyword :name) cols)]
                    ((map (partial zipmap col-names)) conj)))]
    (try
      (reduce-results* honeysql-query context rff [])
      (catch InterruptedException e
        (a/>!! (:canceled-chan context) ::cancel)
        (throw e)))))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                   Helper Fns                                                   |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn user-full-name
  "HoneySQL to grab the full name of a User.

     (user-full-name :u) ;; -> 'Cam Saul'"
  [user-table]
  (hx/concat (hsql/qualify user-table :first_name)
             (hx/literal " ")
             (hsql/qualify user-table :last_name)))

(def datetime-unit-str->base-type
  "Map of datetime unit strings (possible params for queries that accept a datetime `unit` param) to the `:base_type` we
  should use for that column in the results."
  {"quarter"         :type/Date
   "day"             :type/Date
   "hour"            :type/DateTime
   "week"            :type/Date
   "default"         :type/DateTime
   "day-of-week"     :type/Integer
   "hour-of-day"     :type/Integer
   "month"           :type/Date
   "month-of-year"   :type/Integer
   "day-of-month"    :type/Integer
   "year"            :type/Integer
   "day-of-year"     :type/Integer
   "week-of-year"    :type/Integer
   "quarter-of-year" :type/Integer
   "minute-of-hour"  :type/Integer
   "minute"          :type/DateTime})

(def DateTimeUnitStr
  "Scheme for a valid QP DateTime unit as a string (the format they will come into the audit QP). E.g. something
  like `day` or `day-of-week`."
  (apply s/enum (keys datetime-unit-str->base-type)))

(defn grouped-datetime
  "Group a datetime expression by `unit` using the appropriate SQL QP `date` implementation for our application
  database.

    (grouped-datetime :day :timestamp) ;; -> `cast(timestamp AS date)` [honeysql equivalent]"
  [unit expr]
  (sql.qp/date (mdb/db-type) (keyword unit) expr))

(defn first-non-null
  "Build a `CASE` statement that returns the first non-`NULL` of `exprs`."
  [& exprs]
  (apply hsql/call :case (mapcat (fn [expr]
                                   [[:not= expr nil] expr])
                                 exprs)))

(defn zero-if-null
  "Build a `CASE` statement that will replace results of `expr` with `0` when it's `NULL`, perfect for things like
  counts."
  [expr]
  (hsql/call :case [:not= expr nil] expr :else 0))


(defn add-search-clause
  "Add an appropriate `WHERE` clause to `query` to see if any of the `fields-to-search` match `query-string`.

    (add-search-clause {} \"birds\" :t.name :db.name)"
  [query query-string & fields-to-search]
  (h/merge-where query (when (seq query-string)
                         (let [query-string (str \% (str/lower-case query-string) \%)]
                           (cons
                            :or
                            (for [field fields-to-search]
                              [:like (keyword (str "%lower." (name field))) query-string]))))))

(defn card-public-url
  "Return HoneySQL for a `CASE` statement to return a Card's public URL if the `public_uuid` `field` is non-NULL."
  [field]
  (hsql/call :case
    [:not= field nil]
    (hx/concat (urls/public-card-prefix) field)))

(defn native-or-gui
  "Return HoneySQL for a `CASE` statement to format the QueryExecution `:native` column as either `Native` or `GUI`."
  [query-execution-table]
  (hsql/call :case [:= (hsql/qualify query-execution-table :native) true] (hx/literal "Native") :else (hx/literal "GUI")))

(defn card-name-or-ad-hoc
  "HoneySQL for a `CASE` statement to return the name of a Card, or `Ad-hoc` if Card name is `NULL`."
  [card-table]
  (first-non-null (hsql/qualify card-table :name) (hx/literal "Ad-hoc")))

(defn query-execution-is-download
  "HoneySQL for a `WHERE` clause to restrict QueryExecution rows to downloads (i.e. executions returned in CSV/JSON/XLS
  format)."
  [query-execution-table]
  [:in (hsql/qualify query-execution-table :context) #{"csv-download" "xlsx-download" "json-download"}])
