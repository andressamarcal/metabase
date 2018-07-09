(ns metabase.mt.api.field-test
  "Tests for special behavior of `/api/metabase/field` endpoints in the Metabase Enterprise Edition."
  (:require [expectations :refer :all]
            [metabase.mt.api.table-test :as mt-table-test]
            [metabase.test.data :as data]
            [metabase.test.data.users :as users]))

;; Can I fetch a Field that I don't have read access for if I have segmented table access for it?
(expect
  {:name             "NAME"
   :display_name     "Name"
   :has_field_values "list"}
  (mt-table-test/with-segmented-test-setup mt-table-test/restricted-column-query
    (mt-table-test/with-user-attributes :rasta {:cat 50}
      (-> ((users/user->client :rasta) :get 200 (str "field/" (data/id :venues :name)))
          (select-keys [:name :display_name :has_field_values])))))

;; When I call the FieldValues API endpoint for a Field that I have segmented table access only for, will I get ad-hoc
;; values?
(expect
  ;; Rasta Toucan is only allowed to see Venues that are in the "Mexican" category [category_id = 50]. So fetching
  ;; FieldValues for `venue.name` should do an ad-hoc fetch and only return the names of venues in that category.
  {:field_id true
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
  (mt-table-test/with-segmented-test-setup mt-table-test/restricted-column-query
    (mt-table-test/with-user-attributes :rasta {:cat 50}
      (-> ((users/user->client :rasta) :get 200 (str "field/" (data/id :venues :name) "/values"))
          ;; `with-segmented-test-setup` binds `data/db` and `data/id` to a new temp copy of the test data DB so the
          ;; value of the `data/id` call here will be different from if we were to call it in `expected`.
          (update :field_id (partial = (data/id :venues :name)))))))

;; Now in this case recall that the `restricted-column-query` GTAP we're using does *not* include `venues.price` in
;; the results. (Toucan isn't allowed to know the number of dollar signs!) So make sure if we try to fetch the field
;; values instead of seeing `[[1] [2] [3] [4]]` we get no results
(expect
  {:field_id true
   :values   []}
  (mt-table-test/with-segmented-test-setup mt-table-test/restricted-column-query
    (mt-table-test/with-user-attributes :rasta {:cat 50}
      (-> ((users/user->client :rasta) :get 200 (str "field/" (data/id :venues :price) "/values"))
          (update :field_id (partial = (data/id :venues :price)))))))
