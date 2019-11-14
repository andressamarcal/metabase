(ns metabase.mt.api.user-test
  "Tests that would logically be included in `metabase.api.user-test` but are separate as they are enterprise only."
  (:require [expectations :refer :all]
            [metabase.models
             [card :refer [Card]]
             [collection-test :as collection-test]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.mt.test-util :as mt.tu]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data.users :refer :all]
            [metabase.util :as u]
            [toucan.util.test :as tt]))

;; Non-segmented users are allowed to ask for a list of all of the users in the Metabase instance. Pulse email lists
;; are an example usage of this. Segmented users should not have that ability. Instead they should only see
;; themselves. This test checks that GET /api/user for a segmented user only returns themselves
(expect
  [{:common_name "Rasta Toucan", :last_name "Toucan", :first_name "Rasta", :email "rasta@metabase.com", :id true}]
  (mt.tu/with-copy-of-test-db [db]
    (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                  :dataset_query {:database (u/get-id db)
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

      (mt.tu/add-segmented-perms-for-venues-for-all-users-group! db)
      ;; Make sure personal Collections have been created
      (collection-test/force-create-personal-collections!)
      ;; Now do the request
      (tu/boolean-ids-and-timestamps ((user->client :rasta) :get 200 "user")))))
