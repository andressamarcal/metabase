(ns metabase.mt.api.user-test
  "Tests that would logically be included in `metabase.api.user-test` but are separate as they are enterprise only."
  (:require [expectations :refer :all]
            [metabase.models
             [card :refer [Card]]
             [collection-test :as collection-test]
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

;; Non-segmented users are allowed to ask for a list of all of the users in the Metabase instance. Pulse email lists
;; are an example usage of this. Segmented users should not have that ability. Instead they should only see
;; themselves. This test checks that GET /api/user for a segmented user only returns themselves
(expect
  [{:common_name "Rasta Toucan", :last_name "Toucan", :first_name "Rasta", :email "rasta@metabase.com", :id 1}]
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
       ;; Make sure personal Collections have been created
       (collection-test/force-create-personal-collections!)
       ;; Now do the request
       ((user->client :rasta) :get 200 "user")))))
