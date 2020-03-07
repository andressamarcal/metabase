(ns metabase-enterprise.audit.pages.common-test
  (:require [clojure.test :refer :all]
            [metabase
             [db :as mdb]
             [query-processor :as qp]
             [test :as mt]]
            [metabase-enterprise.audit.pages.common :as pages.common]
            [metabase.public-settings.metastore-test :as metastore-test]))

(defn- run-query
  [varr & {:as additional-query-params}]
  (mt/with-test-user :crowberto
    (metastore-test/with-metastore-token-features #{:audit-app}
      (qp/process-query (merge {:type :internal
                                :fn   (let [mta (meta varr)]
                                        (format "%s/%s" (ns-name (:ns mta)) (:name mta)))}
                               additional-query-params)))))

(defn- ^:private ^:internal-query-fn legacy-format-query-fn
  [a1]
  (let [h2? (= (mdb/db-type) :h2)]
    {:metadata [[:A {:display_name "A", :base_type :type/DateTime}]
                [:B {:display_name "B", :base_type :type/Integer}]]
     :results  (pages.common/query
                {:union-all [{:select [[a1 :A] [2 :B]]}
                             {:select [[3 :A] [4 :B]]}]})}))

(defn- ^:private ^:internal-query-fn reducible-format-query-fn
  [a1]
  {:metadata [[:A {:display_name "A", :base_type :type/DateTime}]
              [:B {:display_name "B", :base_type :type/Integer}]]
   :results  (pages.common/reducible-query
              {:union-all [{:select [[a1 :A] [2 :B]]}
                           {:select [[3 :A] [4 :B]]}]})
   :xform    (map #(update (vec %) 0 inc))})

(deftest transform-results-test
  (testing "Make sure query function result are transformed to QP results correctly"
    (metastore-test/with-metastore-token-features #{:audit-app}
      (doseq [[format-name {:keys [varr expected-rows]}] {"legacy"    {:varr          #'legacy-format-query-fn
                                                                       :expected-rows [[100 2] [3 4]]}
                                                          "reducible" {:varr          #'reducible-format-query-fn
                                                                       :expected-rows [[101 2] [4 4]]}}]
        (testing (format "format = %s" format-name)
          (let [results (delay (run-query varr :args [100]))]
            (testing "cols"
              (is (= [{:display_name "A", :base_type :type/DateTime, :name "A"}
                      {:display_name "B", :base_type :type/Integer, :name "B"}]
                     (mt/cols @results))))
            (testing "rows"
              (is (= expected-rows
                     (mt/rows @results))))))))))

(deftest query-limit-and-offset-test
  (testing "Make sure params passed in as part of the query map are respected"
    (metastore-test/with-metastore-token-features #{:audit-app}
      (doseq [[format-name {:keys [varr expected-rows]}] {"legacy"    {:varr          #'legacy-format-query-fn
                                                                       :expected-rows [[100 2] [3 4]]}
                                                          "reducible" {:varr          #'reducible-format-query-fn
                                                                       :expected-rows [[101 2] [4 4]]}}]
        (testing (format "format = %s" format-name)
          (testing :limit
            (is (= [(first expected-rows)]
                   (mt/rows (run-query varr :args [100], :limit 1)))))
          (testing :offset
            (is (= [(second expected-rows)]
                   (mt/rows (run-query varr :args [100], :offset 1))))))))))
