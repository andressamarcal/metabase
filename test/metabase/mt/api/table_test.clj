(ns metabase.mt.api.table-test
  (:require [clojure.string :as str]
            [expectations :refer :all]
            [metabase.mt.test-util :as mt.tu]
            [metabase.test.data :as data]
            [metabase.test.data.users :as users]))

;; Users with restricted access to the columns of a table should only see columns included in the GTAP question
(expect
  ["CATEGORY_ID" "ID" "NAME"]
  (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
    (mt.tu/with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :rasta) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))

(def ^:private all-columns
  ["CATEGORY_ID" "ID" "LATITUDE" "LONGITUDE" "NAME" "PRICE"])

;; Users with full permissions should not be affected by this field filtering
(expect
  all-columns
  (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
    (mt.tu/with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :crowberto) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))

;; If a GTAP has a question, but that question doesn't include a clause to restrict the columns that are returned, all
;; fields should be returned
(expect
  all-columns
  (mt.tu/with-segmented-test-setup (fn [db-id]
                               {:database db-id
                                :type     :query
                                :query    {:source_table (data/id :venues)}})
    (mt.tu/with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :rasta) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))
