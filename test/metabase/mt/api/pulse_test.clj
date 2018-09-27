(ns metabase.mt.api.pulse-test
  "Tests that would logically be included in `metabase.api.pulse-test` but are separate as they are enterprise only."
  (:require  [expectations :refer :all]
             [metabase.models
              [card :refer [Card]]
              [permissions :as perms :refer [Permissions]]
              [permissions-group :as perms-group :refer [PermissionsGroup]]
              [permissions-group-membership :refer [PermissionsGroupMembership]]]
             [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
             [metabase.mt.query-processor.middleware.row-level-restrictions-test :as rlrt]
             [metabase.test
              [data :as data]
              [util :as tu]]
             [metabase.test.data
              [users :refer :all]]
             [toucan.util.test :as tt]))

;; Non-segmented users are able to send pulses to any slack channel that the configured instance can see. A segmented
;; user should not be able to send messages to those channels. This tests that a segmented user doesn't see any slack
;; channels.
(expect
  nil
  (rlrt/call-with-segmented-perms
   (fn [db-id]
     (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                   :dataset_query {:database db-id
                                                                   :type     :native
                                                                   :native   {:query "SELECT * FROM VENUES WHERE category_id = {{cat}}"
                                                                              :template_tags {:cat {:name "cat" :display_name "cat" :type "number" :required true}}}}}]
                     PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                     PermissionsGroupMembership [_ {:group_id group-id
                                                    :user_id  (user->id :rasta)}]
                     GroupTableAccessPolicy [gtap {:group_id group-id
                                                   :table_id (data/id :venues)
                                                   :card_id card-id
                                                   :attribute_remappings {:cat ["variable" ["template-tag" "cat"]]}}]]

       (rlrt/add-segmented-perms db-id)
       (tu/with-temporary-setting-values [slack-token nil]
         (-> ((user->client :rasta) :get 200 "pulse/form_input")
             (get-in [:channels :slack])))))))
