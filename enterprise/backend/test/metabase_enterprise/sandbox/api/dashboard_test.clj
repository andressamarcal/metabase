(ns metabase-enterprise.sandbox.api.dashboard-test
  "Tests for special behavior of `/api/metabase/dashboard` endpoints in the Metabase Enterprise Edition."
  (:require [clojure.test :refer :all]
            [metabase-enterprise.sandbox.test-util :as mt.tu]
            [metabase
             [models :refer [Card Dashboard DashboardCard]]
             [test :as mt]]))

(deftest params-values-test
  (testing "GET /api/dashboard/:id"
    (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
      (mt.tu/with-user-attributes :rasta {:cat 50}
        (mt/with-temp* [Dashboard     [{dashboard-id :id} {:name "Test Dashboard"}]
                        Card          [{card-id :id}      {:name "Dashboard Test Card"}]
                        DashboardCard [{dc-id :id}        {:dashboard_id       dashboard-id
                                                           :card_id            card-id
                                                           :parameter_mappings [{:card_id      card-id
                                                                                 :parameter_id "foo"
                                                                                 :target       [:dimension [:field_id (mt/id :venues :name)]]}]}]]
          ;; Rasta Toucan is only allowed to see Venues that are in the "Mexican" category [category_id = 50]. So
          ;; fetching FieldValues for `venue.name` should do an ad-hoc fetch and only return the names of venues in
          ;; that category.
          (is (= {(keyword (str (mt/id :venues :name)))
                  {:values   [["Garaje"]
                              ["Gordo Taqueria"]
                              ["La Tortilla"]
                              ["Manuel's Original El Tepeyac Cafe"]
                              ["Señor Fish"]
                              ["Tacos Villa Corona"]
                              ["Taqueria Los Coyotes"]
                              ["Taqueria San Francisco"]
                              ["Tito's Tacos"]
                              ["Yuca's Taqueria"]]
                   :field_id (mt/id :venues :name)}}
                 (:param_values ((mt/user->client :rasta) :get 200 (str "dashboard/" dashboard-id))))))))))
