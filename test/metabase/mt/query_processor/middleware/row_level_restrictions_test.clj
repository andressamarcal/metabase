(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [expectations :refer :all]
            [metabase
             [query-processor :as qp]
             [query-processor-test :as qpt]]
            [metabase.models.card :refer [Card]]
            ;;Ensure the row-level-restrictions namespace is loaded so that the middleware will be injected before
            ;;running the below test
            metabase.mt.query-processor.middleware.row-level-restrictions
            [metabase.query-processor.middleware.expand :as ql]
            [metabase.test.data :as data]
            [metabase.test.data.dataset-definitions :as defs]
            [toucan.util.test :as tt]))

(defn- with-user-attributes [query-context user-attributes]
  (assoc query-context :user-attributes user-attributes))

(expect
  [[10]]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp Card [card {:name          "magic"
                              :dataset_query {:database (data/id)
                                              :type     :native
                                              :native   {:query "SELECT * FROM VENUES WHERE category_id = 50"}}}]
      (-> (data/query venues
              (ql/aggregation (ql/count)))
          data/wrap-inner-query
          (with-user-attributes {:foo "bar"})
          qp/process-query
          qpt/rows))))
