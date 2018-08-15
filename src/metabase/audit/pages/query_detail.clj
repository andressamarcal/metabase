(ns metabase.audit.pages.query-detail
  "Queries to show details about a (presumably ad-hoc) query."
  (:require [honeysql.core :as hsql]
            [cheshire.core :as json]
            [metabase.audit.pages.common :as common]
            [metabase.audit.pages.common
             [cards :as cards]
             [dashboards :as dashboards]]
            [metabase.util
             [honeysql-extensions :as hx]
             [schema :as su]
             [urls :as urls]]
            [ring.util.codec :as codec]
            [schema.core :as s]))

(s/defn ^:internal-query-fn details
  [query-hash :- su/NonBlankString]
  {:metadata [[:query                  {:display_name "Query",                :base_type :type/Dictionary}]
              [:average_execution_time {:display_name "Avg. Exec. Time (ms)", :base_type :type/Number}]]
   :results  (->> (common/query
                    {:select [:query
                              :average_execution_time]
                     :from   [:query]
                     :where  [:= :query_hash (codec/base64-decode query-hash)]
                     :limit  1})
                  (map #(update % :query json/parse-string)))})
