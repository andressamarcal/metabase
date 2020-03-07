(ns metabase-enterprise.sandbox.api.field-test
  "Tests for special behavior of `/api/metabase/field` endpoints in the Metabase Enterprise Edition."
  (:require [clojure.test :refer :all]
            [metabase-enterprise.sandbox.test-util :as mt.tu]
            [metabase.test :as mt]))

(deftest fetch-field-test
  (testing "GET /api/field/:id"
    (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
      (mt.tu/with-user-attributes :rasta {:cat 50}
        (testing "Can I fetch a Field that I don't have read access for if I have segmented table access for it?"
          (let [result ((mt/user->client :rasta) :get 200 (str "field/" (mt/id :venues :name)))]
            (is (map? result))
            (is (= {:name             "NAME"
                    :display_name     "Name"
                    :has_field_values "list"}
                   (select-keys result [:name :display_name :has_field_values])))))))))

(deftest field-values-test
  (testing "GET /api/field/:id/values"
    (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
      (mt.tu/with-user-attributes :rasta {:cat 50}
        (testing (str "When I call the FieldValues API endpoint for a Field that I have segmented table access only "
                      "for, will I get ad-hoc values?")
          ;; Rasta Toucan is only allowed to see Venues that are in the "Mexican" category [category_id = 50]. So
          ;; fetching FieldValues for `venue.name` should do an ad-hoc fetch and only return the names of venues in
          ;; that category.
          (let [result ((mt/user->client :rasta) :get 200 (str "field/" (mt/id :venues :name) "/values"))]
            (is (= {:field_id (mt/id :venues :name)
                    :values   [["Garaje"]
                               ["Gordo Taqueria"]
                               ["La Tortilla"]
                               ["Manuel's Original El Tepeyac Cafe"]
                               ["Señor Fish"]
                               ["Tacos Villa Corona"]
                               ["Taqueria Los Coyotes"]
                               ["Taqueria San Francisco"]
                               ["Tito's Tacos"]
                               ["Yuca's Taqueria"]]}
                   result))))

        (testing (str "Now in this case recall that the `restricted-column-query` GTAP we're using does *not* include "
                      "`venues.price` in the results. (Toucan isn't allowed to know the number of dollar signs!) So "
                      "make sure if we try to fetch the field values instead of seeing `[[1] [2] [3] [4]]` we get no "
                      "results")
          (mt/suppress-output
            (let [result ((mt/user->client :rasta) :get 200 (str "field/" (mt/id :venues :price) "/values"))]
              (is (= {:field_id (mt/id :venues :price)
                      :values   []}
                     result)))))))))

(deftest search-test
  (testing "GET /api/field/:id/search/:search-id"
    (mt.tu/with-segmented-test-setup mt.tu/restricted-column-query
      (mt.tu/with-user-attributes :rasta {:cat 50}
        (testing (str "Searching via the query builder needs to use a GTAP when the user has segmented permissions. "
                      "This tests out a field search on a table with segmented permissions")
          ;; Rasta Toucan is only allowed to see Venues that are in the "Mexican" category [category_id = 50]. So
          ;; searching whould only include venues in that category
          (let [url (format "field/%s/search/%s" (mt/id :venues :name) (mt/id :venues :name))]
            (is (= [["Tacos Villa Corona"     "Tacos Villa Corona"]
                    ["Taqueria Los Coyotes"   "Taqueria Los Coyotes"]
                    ["Taqueria San Francisco" "Taqueria San Francisco"]]
                   ((mt/user->client :rasta) :get 200 url :value "Ta")))))))))
