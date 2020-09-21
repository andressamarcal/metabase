(ns metabase-enterprise.sso.integrations.jwt-test
  (:require [buddy.sign
             [jwt :as jwt]
             [util :as buddy-util]]
            [clojure.string :as str]
            [crypto.random :as crypto-random]
            [expectations :refer :all]
            [clojure.test :refer :all]
            [metabase.test.fixtures :as fixtures]
            [metabase.models
             [permissions-group :as group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [user :refer [User]]]
            [metabase-enterprise.sso.integrations
             [jwt :as mt.jwt]
             [saml-test :as saml-test]]
            [metabase.test.data.users :as users]
            [metabase.test.util :as tu]
            [metabase.util :as u]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

(use-fixtures :once (fixtures/initialize :test-users))

(def ^:private default-idp-uri      "http://test.idp.metabase.com")
(def ^:private default-redirect-uri "http://localhost:3000/test")
(def ^:private default-jwt-secret   (crypto-random/hex 32))

;; SSO requests fail if SAML hasn't been enabled
(expect
  (saml-test/with-valid-metastore-token
    (saml-test/client :get 400 "/auth/sso")))

;; SSO requests fail if they don't have a valid metastore token
(expect
  (saml-test/client :get 403 "/auth/sso"))

;; SSO requests fail if SAML is enabled but hasn't been configured
(expect
  (saml-test/with-valid-metastore-token
    (tu/with-temporary-setting-values [jwt-enabled "true"]
      (saml-test/client :get 400 "/auth/sso"))))

;; The IDP provider certificate must also be included for SSO to be configured
(expect
  (saml-test/with-valid-metastore-token
    (tu/with-temporary-setting-values [jwt-enabled "true"
                                       jwt-identity-provider-uri default-idp-uri]
      (saml-test/client :get 400 "/auth/sso"))))

(defn- call-with-default-jwt-config [f]
  (tu/with-temporary-setting-values [jwt-enabled "true"
                                     jwt-identity-provider-uri default-idp-uri
                                     jwt-shared-secret default-jwt-secret]
    (f)))

(defmacro ^:private with-jwt-default-setup [& body]
  `(saml-test/with-valid-metastore-token
     (saml-test/call-with-login-attributes-cleared!
      (fn []
        (call-with-default-jwt-config
         (fn []
           ~@body))))))

; With JWT configured, a GET request should result in a redirect to the IDP
(expect
  (with-jwt-default-setup
    (let [result       (saml-test/client-full-response :get 302 "/auth/sso"
                                                       {:request-options {:follow-redirects false}}
                                                       :redirect default-redirect-uri)
          redirect-url (get-in result [:headers "Location"])]
      (str/starts-with? redirect-url default-idp-uri))))

;; Happy path login, valid JWT, checks to ensure the user was logged in successfully and the redirect to the right
;; location
(expect
  {:successful-login? true
   :redirect-uri      default-redirect-uri
   :login-attributes  {"extra" "keypairs", "are" "also present"}}
  (with-jwt-default-setup
    (let [response (saml-test/client-full-response :get 302 "/auth/sso" {:request-options {:follow-redirects false}}
                                                   :return_to default-redirect-uri
                                                   :jwt (jwt/sign {:email "rasta@metabase.com", :first_name "Rasta", :last_name "Toucan"
                                                                   :extra "keypairs", :are "also present"}
                                                                  default-jwt-secret))]
      {:successful-login? (saml-test/successful-login? response)
       :redirect-uri      (get-in response [:headers "Location"])
       :login-attributes  (db/select-one-field :login_attributes User :email "rasta@metabase.com")})))

(def ^:private ^:const five-minutes-in-seconds (* 60 5))

;; Check an expired JWT
(expect
  "Token is older than max-age (180)"
  (with-jwt-default-setup
    (:message (saml-test/client :get 500 "/auth/sso" {:request-options {:follow-redirects false}}
                                :return_to default-redirect-uri
                                :jwt (jwt/sign {:email "test@metabase.com", :first_name "Test" :last_name "User"
                                                :iat (- (buddy-util/now) five-minutes-in-seconds)}
                                               default-jwt-secret)))))

;; A new account will be created for a JWT user we haven't seen before
(defmacro with-users-with-email-deleted {:style/indent 1} [user-email & body]
  `(try
     ~@body
     (finally
       (db/delete! User :email ~user-email))))

(expect
  {:new-user-exists-before? false
   :successful-login?       true
   :new-user                [{:email "newuser@metabase.com", :first_name "New", :last_login false,
                              :is_qbnewb true, :is_superuser false, :id true, :last_name "User",
                              :date_joined true, :common_name "New User"}]
   :login-attributes        {"more" "stuff", "for" "the new user"}}
  (with-jwt-default-setup
    (with-users-with-email-deleted "newuser@metabase.com"
      (let [new-user-exists? (boolean (seq (db/select User :email "newuser@metabase.com")))
            response         (saml-test/client-full-response :get 302 "/auth/sso"
                                                   {:request-options {:follow-redirects false}}
                                                   :return_to default-redirect-uri
                                                   :jwt (jwt/sign {:email "newuser@metabase.com", :first_name "New", :last_name "User"
                                                                   :more "stuff", :for "the new user"} default-jwt-secret))]
        {:new-user-exists-before? new-user-exists?
         :successful-login?       (saml-test/successful-login? response)
         :new-user                (tu/boolean-ids-and-timestamps (db/select User :email "newuser@metabase.com"))
         :login-attributes        (db/select-one-field :login_attributes User :email "newuser@metabase.com")}))))

;; make sure our setting for mapping group names -> IDs works
(expect
 #{1 2 3 4}
  (tu/with-temporary-setting-values [jwt-group-mappings {"group_1" [1 2 3]
                                                         "group_2" [3 4]
                                                         "group_3" [5]}]
    (#'mt.jwt/group-names->ids [:group_1 :group_2])))

(expect
 #{3 4 5}
  (tu/with-temporary-setting-values [jwt-group-mappings {"group_1" [1 2 3]
                                                         "group_2" [3 4]
                                                         "group_3" [5]}]
    (#'mt.jwt/group-names->ids ["group_2" "group_3"])))

;; login should sync group memberships if enabled
(defn- group-memberships [user-or-id]
  (when-let [group-ids (seq (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id user-or-id)))]
    (db/select-field :name PermissionsGroup :id [:in group-ids])))

(expect
  #{"All Users" ":metabase-enterprise.sso.integrations.jwt-test/my-group"}
  (with-jwt-default-setup
    (tt/with-temp PermissionsGroup [my-group {:name (str ::my-group)}]
      (tu/with-temporary-setting-values [jwt-group-sync       true
                                         jwt-group-mappings   {"my_group" [(u/get-id my-group)]}
                                         jwt-attribute-groups "GrOuPs"]
        (with-users-with-email-deleted "newuser@metabase.com"
          (let [response    (saml-test/client-full-response :get 302 "/auth/sso"
                                                            {:request-options {:follow-redirects false}}
                                                            :return_to default-redirect-uri
                                                            :jwt (jwt/sign {:email      "newuser@metabase.com"
                                                                            :first_name "New"
                                                                            :last_name  "User"
                                                                            :more       "stuff"
                                                                            :GrOuPs     ["my_group"]
                                                                            :for        "the new user"}
                                                                           default-jwt-secret))
                _           (assert (saml-test/successful-login? response))
                new-user-id (u/get-id (db/select-one-id User :email "newuser@metabase.com"))]
            (group-memberships new-user-id)))))))
