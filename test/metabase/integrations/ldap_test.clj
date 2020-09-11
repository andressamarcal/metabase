(ns metabase.integrations.ldap-test
  (:require [clojure.test :refer :all]
            [metabase.integrations.ldap :as ldap]
            [metabase.models.user :as user :refer [User]]
            [metabase.test.integrations.ldap :as ldap.test]
            [metabase.test.util :as tu]
            [toucan.db :as db]))

(defn- get-ldap-details []
  {:host       "localhost"
   :port       (ldap.test/get-ldap-port)
   :bind-dn    "cn=Directory Manager"
   :password   "password"
   :security   "none"
   :user-base  "dc=metabase,dc=com"
   :group-base "dc=metabase,dc=com"})

;; See test_resources/ldap.ldif for fixtures

;; The connection test should pass with valid settings
(deftest connection-test
  (testing "anonymous binds"
    (testing "successfully connect to IPv4 host"
      (is (= {:status :SUCCESS}
             (ldap.test/with-ldap-server
               (ldap/test-ldap-connection (get-ldap-details)))))))

  (testing "invalid user search base"
    (is (= :ERROR
           (ldap.test/with-ldap-server
             (:status (ldap/test-ldap-connection (assoc (get-ldap-details)
                                                        :user-base "dc=example,dc=com")))))))

  (testing "invalid group search base"
    (is (= :ERROR
           (ldap.test/with-ldap-server
             (:status (ldap/test-ldap-connection (assoc (get-ldap-details) :group-base "dc=example,dc=com")))))))

  (testing "invalid bind DN"
    (is (= :ERROR
           (ldap.test/with-ldap-server
             (:status (ldap/test-ldap-connection (assoc (get-ldap-details) :bind-dn "cn=Not Directory Manager")))))))

  (testing "invalid bind password"
    (is (= :ERROR
           (ldap.test/with-ldap-server
             (:status (ldap/test-ldap-connection (assoc (get-ldap-details) :password "wrong")))))))

  (testing "basic get-connection works, will throw otherwise"
    (is (= nil
           (ldap.test/with-ldap-server
             (.close (#'ldap/get-connection))))))

  (testing "login should succeed"
    (is (= true
           (ldap.test/with-ldap-server
             (ldap/verify-password "cn=Directory Manager" "password")))))

  (testing "wrong password"
    (is (= false
           (ldap.test/with-ldap-server
             (ldap/verify-password "cn=Directory Manager" "wrongpassword")))))

  (testing "invalid DN fails"
    (is (= false
           (ldap.test/with-ldap-server
             (ldap/verify-password "cn=Nobody,ou=nowhere,dc=metabase,dc=com" "password")))))

  (testing "regular user login"
    (is (= true
           (ldap.test/with-ldap-server
             (ldap/verify-password "cn=Sally Brown,ou=People,dc=metabase,dc=com" "1234")))))

  (testing "fail regular user login with bad password"
    (is (= false
           (ldap.test/with-ldap-server
             (ldap/verify-password "cn=Sally Brown,ou=People,dc=metabase,dc=com" "password")))))

  (testing "LDAP group matching should identify Metabase groups using DN equality rules"
    (is (= #{1 2 3}
           (tu/with-temporary-setting-values
             [ldap-group-mappings {"cn=accounting,ou=groups,dc=metabase,dc=com" [1 2]
                                   "cn=shipping,ou=groups,dc=metabase,dc=com" [2 3]}]
             (#'ldap/ldap-groups->mb-group-ids ["CN=Accounting,OU=Groups,DC=metabase,DC=com"
                                                "CN=Shipping,OU=Groups,DC=metabase,DC=com"]))))))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                 Attribute Sync                                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

(deftest attribute-sync-test
  (testing "find by email/username should return other attributes as well"
    (is (= {:dn         "cn=Lucky Pigeon,ou=Birds,dc=metabase,dc=com"
            :first-name "Lucky"
            :last-name  "Pigeon"
            :email      "lucky@metabase.com"
            :attributes {:uid       "lucky"
                         :mail      "lucky@metabase.com"
                         :title     "King Pigeon"
                         :givenname "Lucky"
                         :sn        "Pigeon"
                         :cn        "Lucky Pigeon"}
            :groups     []}
           (ldap.test/with-ldap-server
             (ldap/find-user "lucky")))))

  (testing "ignored attributes should not be returned"
    (is (= {:dn         "cn=Lucky Pigeon,ou=Birds,dc=metabase,dc=com"
            :first-name "Lucky"
            :last-name  "Pigeon"
            :email      "lucky@metabase.com"
            :attributes {:uid       "lucky"
                         :mail      "lucky@metabase.com"
                         :givenname "Lucky"
                         :sn        "Pigeon"
                         :cn        "Lucky Pigeon"}
            :groups     []}
           (tu/with-temporary-setting-values [ldap-sync-user-attributes-blacklist
                                              (cons "title" (ldap/ldap-sync-user-attributes-blacklist))]
             (ldap.test/with-ldap-server
               (ldap/find-user "lucky"))))))

  (testing "if attribute sync is disabled, no attributes should come back at all"
    (is (= {:dn         "cn=Lucky Pigeon,ou=Birds,dc=metabase,dc=com"
            :first-name "Lucky"
            :last-name  "Pigeon"
            :email      "lucky@metabase.com"
            :attributes nil
            :groups     []}
           (tu/with-temporary-setting-values [ldap-sync-user-attributes false]
             (ldap.test/with-ldap-server
               (ldap/find-user "lucky"))))))

  (testing "when creating a new user, user attributes should get synced"
    (is (= (user/map->UserInstance
            {:first_name  "John"
             :last_name   "Smith"
             :email       "John.Smith@metabase.com"
             :login_attributes {"uid"       "jsmith1"
                                "mail"      "John.Smith@metabase.com"
                                "givenname" "John"
                                "sn"        "Smith"
                                "cn"        "John Smith"}
             :common_name "John Smith"})
           (ldap.test/with-ldap-server
             (try
               (ldap/fetch-or-create-user! (ldap/find-user "jsmith1"))
               (db/select-one [User :first_name :last_name :email :login_attributes] :email "John.Smith@metabase.com")
               (finally
                 (db/delete! User :email "John.Smith@metabase.com")))))))

  (testing "when creating a new user and attribute sync is disabled, attributes should not be synced"
    (is (= (user/map->UserInstance
            {:first_name       "John"
             :last_name        "Smith"
             :email            "John.Smith@metabase.com"
             :login_attributes nil
             :common_name      "John Smith"})
           (ldap.test/with-ldap-server
             (tu/with-temporary-setting-values [ldap-sync-user-attributes false]
               (try
                 (ldap/fetch-or-create-user! (ldap/find-user "jsmith1"))
                 (db/select-one [User :first_name :last_name :email :login_attributes] :email "John.Smith@metabase.com")
                 (finally
                   (db/delete! User :email "John.Smith@metabase.com")))))))))

(deftest update-attributes-on-login-test
  (testing "Existing user's attributes are updated on fetch"
    (is (= {:first_name       "John"
            :last_name        "Smith"
            :common_name      "John Smith"
            :email            "John.Smith@metabase.com"
            :login_attributes {"uid"          "jsmith1"
                               "mail"         "John.Smith@metabase.com"
                               "givenname"    "John"
                               "sn"           "Smith"
                               "cn"           "John Smith"
                               "unladenspeed" 100}}
           (try
            (ldap.test/with-ldap-server
              (let [user-info    (ldap/find-user "jsmith1")]
                ;; First let a user get created for John Smith
                (ldap/fetch-or-create-user! user-info)
                ;; Call fetch-or-create-user! again to trigger update
                (ldap/fetch-or-create-user! (assoc-in user-info [:attributes :unladenspeed] 100))
                (into {} (db/select-one [User :first_name :last_name :email :login_attributes]
                                        :email "John.Smith@metabase.com"))))
            (finally
             (db/delete! User :email "John.Smith@metabase.com"))))))

  (testing "Existing user's attributes are not updated on fetch, when attribute sync is disabled"
    (is (= {:first_name       "John"
            :last_name        "Smith"
            :common_name      "John Smith"
            :email            "John.Smith@metabase.com"
            :login_attributes nil}
           (try
            (ldap.test/with-ldap-server
              (tu/with-temporary-setting-values [ldap-sync-user-attributes false]
                (let [user-info    (ldap/find-user "jsmith1")]
                  ;; First let a user get created for John Smith
                  (ldap/fetch-or-create-user! user-info)
                  ;; Call fetch-or-create-user! again to trigger update
                  (ldap/fetch-or-create-user! (assoc-in user-info [:attributes :unladenspeed] 100))
                  (into {} (db/select-one [User :first_name :last_name :email :login_attributes]
                                          :email "John.Smith@metabase.com")))))
            (finally
             (db/delete! User :email "John.Smith@metabase.com")))))))

;; For hosts that do not support IPv6, the connection code will return an error
;; This isn't a failure of the code, it's a failure of the host.
(deftest ipv6-test
  (testing "successfully connect to IPv6 host"
    (let [actual (ldap.test/with-ldap-server
                   (ldap/test-ldap-connection (assoc (get-ldap-details)
                                                     :host "[::1]")))]
      (if (= (:status actual) :ERROR)
        (is (re-matches #"An error occurred while attempting to connect to server \[::1].*" (:message actual)))
        (is (= {:status :SUCCESS} actual))))))
