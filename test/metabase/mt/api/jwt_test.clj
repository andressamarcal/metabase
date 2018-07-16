(ns metabase.mt.api.jwt-test
  (:require [buddy.sign
             [jwt :as jwt]
             [util :as buddy-util]]
            [clojure.string :as str]
            [expectations :refer :all]
            [metabase.models.user :refer [User]]
            [metabase.mt.api.saml-test :as saml-test]
            [metabase.test.data.users :as users]
            [metabase.test.util :as tu]
            [toucan.db :as db]))

(def ^:private default-idp-uri      "http://test.idp.metabase.com")
(def ^:private default-redirect-uri "http://localhost:3000/test")
(def ^:private default-jwt-secret   "some long secret text for a key")

;; SSO requests fail if SAML hasn't been enabled
(expect
  (saml-test/client :get 400 "/auth/sso"))

;; SSO requests fail if SAML is enabled but hasn't been configured
(expect
  (tu/with-temporary-setting-values [jwt-enabled "true"]
    (saml-test/client :get 400 "/auth/sso")))

;; The IDP provider certificate must also be included for SSO to be configured
(expect
  (tu/with-temporary-setting-values [jwt-enabled "true"
                                     jwt-identity-provider-uri default-idp-uri]
    (saml-test/client :get 400 "/auth/sso")))

(defn- call-with-default-jwt-config [f]
  (tu/with-temporary-setting-values [jwt-enabled "true"
                                     jwt-identity-provider-uri default-idp-uri
                                     jwt-shared-secret default-jwt-secret]
    (f)))

(defmacro ^:private with-jwt-default-setup [& body]
  `(saml-test/call-with-login-attributes-cleared!
    (fn []
      (users/create-users-if-needed!)
      (call-with-default-jwt-config
       (fn []
         ~@body)))))

; With JWT configured, a GET request should result in a redirect to the IDP
(expect
  (with-jwt-default-setup
    (let [result       (saml-test/client-full-response :get 302 "/auth/sso" {:request-options {:follow-redirects false}} :redirect default-redirect-uri)
          redirect-url (get-in result [:headers "Location"])]
      (str/starts-with? redirect-url default-idp-uri))))

;; Happy path login, valid JWT, checks to ensure the user was logged in successfully and the redirect to the right location
(expect
  {:successful-login? true
   :redirect-uri      default-redirect-uri}
  (with-jwt-default-setup
    (let [response (saml-test/client-full-response :get 302 "/auth/sso" {:request-options {:follow-redirects false}}
                                                   :return_to default-redirect-uri
                                                   :jwt (jwt/sign {:email "test@metabase.com", :first_name "Test" :last_name "User"} default-jwt-secret))]
      {:successful-login? (saml-test/successful-login? response)
       :redirect-uri      (get-in response [:headers "Location"])})))

(def ^:private five-minutes-in-seconds (* 60 5))

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
(expect
  {:new-user-exists-before? false
   :successful-login?       true
   :new-user                [{:email "newuser@metabase.com", :first_name "New", :last_login false,
                              :is_qbnewb true, :is_superuser false, :id true, :last_name "User",
                              :date_joined true, :common_name "New User"}]}
  (with-jwt-default-setup
    (try
      (let [new-user-exists? (boolean (seq (db/select User :email "newuser@metabase.com")))
            response         (saml-test/client-full-response :get 302 "/auth/sso"
                                                   {:request-options {:follow-redirects false}}
                                                   :return_to default-redirect-uri
                                                   :jwt (jwt/sign {:email "newuser@metabase.com", :first_name "New" :last_name "User"} default-jwt-secret))]
        {:new-user-exists-before? new-user-exists?
         :successful-login?       (saml-test/successful-login? response)
         :new-user                (tu/boolean-ids-and-timestamps (db/select User :email "newuser@metabase.com"))})
      (finally
        (db/delete! User :email "newuser@metabase.com")))))
