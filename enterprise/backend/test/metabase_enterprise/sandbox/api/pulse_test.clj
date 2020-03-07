(ns metabase-enterprise.sandbox.api.pulse-test
  "Tests that would logically be included in `metabase.api.pulse-test` but are separate as they are enterprise only."
  (:require [expectations :refer :all]
            [metabase.models
             [card :refer [Card]]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]]
            [metabase-enterprise.sandbox.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase-enterprise.sandbox.test-util :as mt.tu]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data.users :refer :all]
            [metabase.util :as u]
            [toucan.util.test :as tt]))

;; Non-segmented users are able to send pulses to any slack channel that the configured instance can see. A segmented
;; user should not be able to send messages to those channels. This tests that a segmented user doesn't see any slack
;; channels.
(expect
  nil
  (mt.tu/with-copy-of-test-db [db]
    (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                  :dataset_query {:database (u/get-id db)
                                                                  :type     :native
                                                                  :native   {:query         "SELECT * FROM VENUES WHERE category_id = {{cat}}"
                                                                             :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
                    PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                    PermissionsGroupMembership [_ {:group_id group-id
                                                   :user_id  (user->id :rasta)}]
                    GroupTableAccessPolicy [gtap {:group_id             group-id
                                                  :table_id             (data/id :venues)
                                                  :card_id              card-id
                                                  :attribute_remappings {:cat ["variable" ["template-tag" "cat"]]}}]]

      (mt.tu/add-segmented-perms-for-venues-for-all-users-group! db)
      (tu/with-temporary-setting-values [slack-token nil]
        (-> ((user->client :rasta) :get 200 "pulse/form_input")
            (get-in [:channels :slack]))))))
