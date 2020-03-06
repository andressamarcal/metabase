(ns metabase.audit.pages.common-test
  (:require [clojure.test :refer :all]
            [metabase
             [query-processor :as qp]
             [test :as mt]]
            [metabase.audit.pages.common :as pages.common]
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
  {:metadata [[:a {:display_name "A", :base_type :type/DateTime}]
              [:b {:display_name "B", :base_type :type/Integer}]]
   :results  (pages.common/query
              {:union-all [{:select [[a1 :a] [2 :b]]}
                           {:select [[3 :a] [4 :b]]}]})})

(defn- ^:private ^:internal-query-fn reducible-format-query-fn
  [a1]
  {:metadata [[:a {:display_name "A", :base_type :type/DateTime}]
              [:b {:display_name "B", :base_type :type/Integer}]]
   :results  (pages.common/reducible-query
              {:union-all [{:select [[a1 :a] [2 :b]]}
                           {:select [[3 :a] [4 :b]]}]})
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
              (is (= [{:display_name "A", :base_type :type/DateTime, :name "a"}
                      {:display_name "B", :base_type :type/Integer, :name "b"}]
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
