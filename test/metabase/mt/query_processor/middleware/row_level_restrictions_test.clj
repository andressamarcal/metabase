(ns metabase.mt.query-processor.middleware.row-level-restrictions-test
  (:require [expectations :refer :all]
            [metabase
             [middleware :as mid]
             [query-processor :as qp]
             [query-processor-test :as qpt]]
            [metabase.models.card :refer [Card]]
            ;;Ensure the row-level-restrictions namespace is loaded so that the middleware will be injected before
            ;;running the below test
            metabase.mt.query-processor.middleware.row-level-restrictions
            [metabase.query-processor.middleware.expand :as ql]
            [metabase.test.data :as data]
            [metabase.test.data
             [dataset-definitions :as defs]
             [users :refer [fetch-user]]]
            [toucan.util.test :as tt]))

(defn- with-user-attributes [query-context user-attributes]
  (assoc query-context :user (assoc (#'mid/find-user (:id (fetch-user :rasta)))
                               :login_attributes user-attributes)))

(expect
  [[10]]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp Card [card {:name          "magic"
                              :dataset_query {:database (data/id)
                                              :type     :native
                                              :native   {:query "SELECT * FROM VENUES WHERE category_id = {{cat}}"
                                                         :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
      (-> (data/query venues
              (ql/aggregation (ql/count)))
          data/wrap-inner-query
          (with-user-attributes {:cat 50})
          qp/process-query
          qpt/rows))))

(expect
  [[10]]
  (data/with-db (data/get-or-create-database! defs/test-data)
    (tt/with-temp Card [card {:name          "magic"
                              :dataset_query {:database (data/id)
                                              :type     :query
                                              :query    {:source_table (data/id :venues)
                                                         :filter ["=" ["field-id" (data/id :venues :category_id)]
                                                                  ["param-value" (data/id :venues :category_id) "cat"]]}}}]
      (-> (data/query venues
            (ql/aggregation (ql/count)))
          data/wrap-inner-query
          (with-user-attributes {:cat 50})
          qp/process-query
          qpt/rows))))
