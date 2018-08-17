(ns metabase.audit.pages.common
  "Shared functions used by audit internal queries across different namespaces."
  (:require [clojure.string :as str]
            [honeysql
             [core :as hsql]
             [helpers :as h]]
            [metabase
             [db :as mdb]
             [driver :as driver]]
            [metabase.driver.generic-sql :as generic-sql]
            [metabase.query-processor.middleware.internal-queries :as internal-queries]
            [metabase.util.honeysql-extensions :as hx]
            [schema.core :as s]
            [toucan.db :as db]))

(def ^:private ^:const default-limit 1000)

(defn query {:style/indent 0} [query-map]
  (let [{:keys [limit offset]} internal-queries/*additional-query-params*]
    (db/query (merge
               {:limit  (or limit default-limit)
                :offset (or offset 0)}
               query-map))))


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
  "Group a datetime expression by `unit` using the appropriate Generic SQL `date` implementation for our application
  database.

    (grouped-datetime :day :timestamp) ;; -> `cast(timestamp AS date)` [honeysql equivalent]"
  [unit expr]
  (generic-sql/date (driver/engine->driver (mdb/db-type))
                    (keyword unit)
                    expr))

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
