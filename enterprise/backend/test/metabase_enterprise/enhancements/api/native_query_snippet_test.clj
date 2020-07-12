(ns metabase-enterprise.enhancements.api.native-query-snippet-test
  (:require [clojure.test :refer :all]
            [metabase
             [models :refer [Collection NativeQuerySnippet]]
             [test :as mt]]
            [metabase.models
             [collection :as collection]
             [permissions :as perms]
             [permissions-group :as group]]
            [metabase.public-settings.metastore-test :as metastore-test]
            [toucan.db :as db]))

(def ^:private root-collection (assoc collection/root-collection :name "Root Collection"))

(deftest move-perms-test
  (testing "PUT /api/native-query-snippet/:id"
    (testing "\nPerms for moving a Snippet"
      (mt/with-non-admin-groups-no-root-collection-perms
        (mt/with-temp* [Collection [source {:name "Current Parent Collection", :namespace "snippets"}]
                        Collection [dest   {:name "New Parent Collection", :namespace "snippets"}]]
          (doseq [source-collection [source root-collection]]
            (mt/with-temp NativeQuerySnippet [snippet {:collection_id (:id source-collection)}]
              (doseq [dest-collection [dest root-collection]]
                (letfn [(has-perms? []
                          ;; make sure the Snippet is back in the original Collection if it was changed
                          (db/update! NativeQuerySnippet (:id snippet) :collection_id (:id source-collection))
                          (let [response ((mt/user->client :rasta) :put (format "native-query-snippet/%d" (:id snippet))
                                          {:collection_id (:id dest-collection)})]
                            (cond
                              (= response "You don't have permissions to do that.")                     false
                              (and (map? response) (= (:collection_id response) (:id dest-collection))) true
                              :else                                                                     response)))]
                  (when-not (= source-collection dest-collection)
                    (testing (format "\nMove from %s -> %s should need write ('curate') perms for both" (:name source-collection) (:name dest-collection))
                      (testing "\nShould be allowed if EE perms aren't enabled"
                        (metastore-test/with-metastore-token-features #{}
                          (is (= true
                                 (has-perms?)))))
                      (metastore-test/with-metastore-token-features #{:enhancements}
                        (doseq [c [source-collection dest-collection]]
                          (testing (format "\nPerms for only %s should fail" (:name c))
                            (try
                              (perms/grant-collection-readwrite-permissions! (group/all-users) c)
                              (is (= false
                                     (has-perms?)))
                              (finally
                                (perms/revoke-collection-permissions! (group/all-users) c)))))
                        (testing "\nShould succeed with both"
                          (try
                            (doseq [c [source-collection dest-collection]]
                              (perms/grant-collection-readwrite-permissions! (group/all-users) c))
                            (is (= true
                                   (has-perms?)))
                            (finally
                              (doseq [c [source-collection dest-collection]]
                                (perms/revoke-collection-permissions! (group/all-users) c)))))))))))))))))
