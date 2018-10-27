(ns metabase.mt.api.table-test
  (:require [clojure.string :as str]
            [expectations :refer :all]
            [metabase.models
             [card :refer [Card]]
             [permissions-group :as perms-group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [user :refer [User]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.mt.query-processor.middleware.row-level-restrictions-test :as rlrt]
            [metabase.test.data :as data]
            [metabase.test.data.users :as users]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

(defn call-with-user-attributes [user-kwd attributes f]
  (let [attributes-before-change (db/select-one-field :login_attributes User :id (users/user->id user-kwd))]
    (try
      (db/update! User (users/user->id user-kwd) {:login_attributes attributes})
      (f)
      (finally
        (db/update! User (users/user->id user-kwd)  {:login_attributes attributes-before-change})))))

(defmacro with-user-attributes [user-kwd attributes-map & body]
  `(call-with-user-attributes ~user-kwd ~attributes-map (fn [] ~@body)))

(defn restricted-column-query [db-id]
  {:database db-id
   :type     :query
   :query    {:source_table (data/id :venues)
              :fields [[:field-id (data/id :venues :id)]
                       [:field-id (data/id :venues :name)]
                       [:field-id (data/id :venues :category_id)]]}})

(defn call-with-segmented-test-setup [make-query-fn f]
  (rlrt/call-with-segmented-perms
   (fn [db-id]
     (let [attr-remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}]
       (tt/with-temp* [Card [{card-id :id :as card} {:name          "magic"
                                                     :dataset_query (make-query-fn db-id)}]
                       PermissionsGroup [{group-id :id} {:name "Restricted Venues"}]
                       PermissionsGroupMembership [_ {:group_id group-id
                                                      :user_id  (users/user->id :rasta)}]
                       GroupTableAccessPolicy [gtap {:group_id             group-id
                                                     :table_id             (data/id :venues)
                                                     :card_id              card-id
                                                     :attribute_remappings attr-remappings}]]
         (rlrt/add-segmented-perms! db-id)
         (f))))))

(defmacro with-segmented-test-setup [make-query-fn & body]
  `(call-with-segmented-test-setup ~make-query-fn (fn [] ~@body)))

;; Users with restricted access to the columns of a table should only see columns included in the GTAP question
(expect
  ["CATEGORY_ID" "ID" "NAME"]
  (with-segmented-test-setup restricted-column-query
    (with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :rasta) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))

(def ^:private all-columns
  ["CATEGORY_ID" "ID" "LATITUDE" "LONGITUDE" "NAME" "PRICE"])

;; Users with full permissions should not be affected by this field filtering
(expect
  all-columns
  (with-segmented-test-setup restricted-column-query
    (with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :crowberto) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))

;; If a GTAP has a question, but that question doesn't include a clause to restrict the columns that are returned, all
;; fields should be returned
(expect
  all-columns
  (with-segmented-test-setup (fn [db-id]
                               {:database db-id
                                :type     :query
                                :query    {:source_table (data/id :venues)}})
    (with-user-attributes :rasta {:cat 50}
      (map (comp str/upper-case :name)
           (:fields ((users/user->client :rasta) :get 200 (format "table/%d/query_metadata" (data/id :venues))))))))
