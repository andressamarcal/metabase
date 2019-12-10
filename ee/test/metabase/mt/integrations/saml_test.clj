(ns metabase.mt.integrations.saml-test
  (:require [clojure
             [string :as str]
             [test :refer :all]]
            [expectations :refer :all]
            [metabase
             [config :as config]
             [http-client :as http]
             [public-settings :as public-settings]
             [util :as u]]
            [metabase.middleware.session :as mw.session]
            [metabase.models
             [permissions-group :as group :refer [PermissionsGroup]]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [user :refer [User]]]
            [metabase.mt.integrations
             [saml :as saml :refer :all]
             [sso-settings :as sso-settings]]
            [metabase.public-settings.metastore :as metastore]
            [metabase.test
             [fixtures :as fixtures]
             [util :as tu]]
            [toucan.db :as db]
            [toucan.util.test :as tt])
  (:import java.net.URL
           java.nio.charset.StandardCharsets
           org.apache.http.client.utils.URLEncodedUtils
           org.apache.http.message.BasicNameValuePair))

(use-fixtures :once (fixtures/initialize :test-users))

(defmacro with-valid-metastore-token
  "Stubs the `metastore/enable-sso?` function to simulate a valid token. This needs to be included to test any of the
  SSO features"
  [& body]
  `(with-redefs [metastore/enable-sso? (constantly true)]
     ~@body))

(defn client
  "Same as `http/client` but doesn't include the `/api` in the URL prefix"
  [& args]
  (binding [http/*url-prefix* (str "http://localhost:" (config/config-str :mb-jetty-port))]
    (apply http/client args)))

(defn client-full-response
  "Same as `http/client-full-response` but doesn't include the `/api` in the URL prefix"
  [& args]
  (binding [http/*url-prefix* (str "http://localhost:" (config/config-str :mb-jetty-port))]
    (apply http/client-full-response args)))

(defn successful-login?
  "Return true if the response indicates a successful user login"
  [resp]
  (string? (get-in resp [:cookies @#'mw.session/metabase-session-cookie :value])))

(def ^:private default-idp-uri            "http://test.idp.metabase.com")
(def ^:private default-redirect-uri       "http://localhost:3000/test")
(def ^:private default-idp-uri-with-param (str default-idp-uri "?someparam=true"))

(def ^:private default-idp-cert
  "Public certificate from Auth0, used to validate mock SAML responses from Auth0"
  "MIIDEzCCAfugAwIBAgIJYpjQiNMYxf1GMA0GCSqGSIb3DQEBCwUAMCcxJTAjBgNV
BAMTHHNhbWwtbWV0YWJhc2UtdGVzdC5hdXRoMC5jb20wHhcNMTgwNTI5MjEwMDIz
WhcNMzIwMjA1MjEwMDIzWjAnMSUwIwYDVQQDExxzYW1sLW1ldGFiYXNlLXRlc3Qu
YXV0aDAuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzNcrpju4
sILZQNe1adwg3beXtAMFGB+Buuc414+FDv2OG7X7b9OSYar/nsYfWwiazZRxEGri
agd0Sj5mJ4Qqx+zmB/r4UgX3q/KgocRLlShvvz5gTD99hR7LonDPSWET1E9PD4XE
1fRaq+BwftFBl45pKTcCR9QrUAFZJ2R/3g06NPZdhe4bg/lTssY5emCxaZpQEku/
v+zzpV2nLF4by0vSj7AHsubrsLgsCfV3JvJyTxCyo1aIOlv4Vrx7h9rOgl9eEmoU
5XJAl3D7DuvSTEOy7MyDnKF17m7l5nOPZCVOSzmCWvxSCyysijgsM5DSgAE8DPJy
oYezV3gTX2OO2QIDAQABo0IwQDAPBgNVHRMBAf8EBTADAQH/MB0GA1UdDgQWBBSp
B3lvrtbSDuXkB6fhbjeUpFmL2DAOBgNVHQ8BAf8EBAMCAoQwDQYJKoZIhvcNAQEL
BQADggEBAAEHGIAhR5GPD2JxgLtpNtZMCYiAM4Gr7hoTQMaKiXgVtdQu4iMFfbpE
wIr6UVaDU2HKhvSRFIilOjRGmCGrIzvJgR2l+RL1Z3KrZypI1AXKJT5pF5g5FitB
sZq+kiUpdRILl2hICzw9Q1M2Le+JSUcHcbHTVgF24xuzOZonxeE56Oc26Ju4CorL
pM3Nb5iYaGOlQ+48/GP82cLxlVyi02va8tp7KP03ePSaZeBEKGpFtBtEN/dC3NKO
1mmrT9284H0tvete6KLUH+dsS6bDEYGHZM5KGoSLWRr3qYlCB3AmAw+KvuiuSczL
g9oYBkdxlhK9zZvkjCgaLCen+0aY67A=")

;; make sure our test certificate is actually valid
(expect
  (#'sso-settings/validate-saml-idp-cert default-idp-cert))

;; SSO requests fail if SAML hasn't been enabled
(expect
  (with-valid-metastore-token
    (client :get 400 "/auth/sso")))

;; SSO requests fail if they don't have a valid metastore token
(expect
  (client :get 403 "/auth/sso"))

;; SSO requests fail if SAML is enabled but hasn't been configured
(expect
  (with-valid-metastore-token
    (tu/with-temporary-setting-values [saml-enabled "true"]
      (client :get 400 "/auth/sso"))))

;; The IDP provider certificate must also be included for SSO to be configured
(expect
  (with-valid-metastore-token
    (tu/with-temporary-setting-values [saml-enabled "true"
                                       saml-identity-provider-uri default-idp-uri]
      (client :get 400 "/auth/sso"))))

;;
;; The basic flow of of a SAML login is below
;;
;; 1. User attempts to access <URL> but is not authenticated
;; 2. User is redirected to GET /auth/sso
;; 3. Metabase issues another redirect to the identity provider URI
;; 4. User logs into their identity provider (i.e. Auth0)
;; 5. Identity provider POSTs to Metabase with successful auth info
;; 6. Metabase parses/validates the SAML response
;; 7. Metabase inits the user session, responds with a redirect to back to the original <URL>
;;

(defn- call-with-default-saml-config [f]
  (tu/with-temporary-setting-values [saml-enabled "true"
                                     saml-identity-provider-uri default-idp-uri
                                     saml-identity-provider-certificate default-idp-cert]
    (f)))

(defn call-with-login-attributes-cleared!
  "If login_attributes remain after these tests run, depending on the order that the tests run, lots of tests will
  fail as the login_attributes data from this tests is unexpected in those other tests"
  [f]
  (try
    (f)
    (finally
      (u/ignore-exceptions (db/update-where! User {} :login_attributes nil)))))

(defmacro ^:private with-saml-default-setup [& body]
  `(with-valid-metastore-token
     (call-with-login-attributes-cleared!
      (fn []
        (call-with-default-saml-config
         (fn []
           ~@body))))))

; With SAML configured, a GET request should result in a redirect to the IDP
(expect
  (with-saml-default-setup
    (let [result       (client-full-response :get 302 "/auth/sso"
                                             {:request-options {:follow-redirects false}}
                                             :redirect default-redirect-uri)
          redirect-url (get-in result [:headers "Location"])]
      (str/starts-with? redirect-url default-idp-uri))))

;; TODO - maybe this belongs in a util namespace?
(defn- uri->params-map
  "Parse the URI string, creating a map from the key/value pairs in the query string"
  [uri-str]
  (into
   {}
   (for [^BasicNameValuePair pair (-> (URL. uri-str) .getQuery (URLEncodedUtils/parse StandardCharsets/UTF_8))]
     [(keyword (.getName pair)) (.getValue pair)])))

(expect
  {:a "b", :c "d"}
  (uri->params-map "http://localhost?a=b&c=d"))

;; When the identity provider already includes a query parameter, the SAML code should spot that and append more
;; parameters onto the query string (rather than always include a `?newparam=here`).
(expect
  #{:someparam :SAMLRequest :RelayState}
  (with-saml-default-setup
    (tu/with-temporary-setting-values [saml-identity-provider-uri default-idp-uri-with-param]
      (let [result       (client-full-response :get 302 "/auth/sso"
                                               {:request-options {:follow-redirects false}}
                                               :redirect default-redirect-uri)
            redirect-url (get-in result [:headers "Location"])]
        (set (keys (uri->params-map redirect-url)))))))

;; The RelayState is data we include in the redirect request to the IDP. The IDP will include the RelayState in it's
;; response via the POST. This allows the FE to track what the original route the user was trying to access was and
;; redirect the user back to that original URL after successful authentication
;;
;; This tests to ensure that we are including the redirect in the params, but it's encrypted, so we need to decrypt it
;; to validate we are including the right thing
(expect
  default-redirect-uri
  (with-saml-default-setup
    (let [result       (client-full-response :get 302 "/auth/sso"
                                             {:request-options {:follow-redirects false}}
                                             :redirect default-redirect-uri)
          redirect-url (get-in result [:headers "Location"])]
      (#'saml/decrypt-relay-state (:RelayState (uri->params-map redirect-url) )))))

(defn- saml-response-from-file [filename]
  (u/encode-base64 (slurp filename)))

(defn- saml-test-response []
  (saml-response-from-file "test_resources/saml-test-response.xml"))

(defn- new-user-saml-test-response []
  (saml-response-from-file "test_resources/saml-test-response-new-user.xml"))

(defn- new-user-with-single-group-saml-test-response []
  (saml-response-from-file "test_resources/saml-test-response-new-user-with-single-group.xml"))

(defn- new-user-with-groups-saml-test-response []
  (saml-response-from-file "test_resources/saml-test-response-new-user-with-groups.xml"))

(defn- saml-post-request-options [saml-response relay-state]
  {:request-options {:content-type     :x-www-form-urlencoded
                     :follow-redirects false
                     :form-params      {:SAMLResponse saml-response
                                        :RelayState   relay-state}}})

(defn- some-saml-attributes [user-nickname]
  {"http://schemas.auth0.com/identities/default/provider"   "auth0"
   "http://schemas.auth0.com/nickname"                      user-nickname
   "http://schemas.auth0.com/identities/default/connection" "Username-Password-Authentication"})

(defn- saml-login-attributes [email]
  (let [attribute-keys (keys (some-saml-attributes nil))]
    (-> (db/select-one-field :login_attributes User :email email)
        (select-keys attribute-keys))))

;; After a successful login with the identity provider, the SAML provider will POST to the `/auth/sso` route.
;;
;; Part of accepting the POST is validating the response and the relay state so we can redirect the user to their
;; original destination
(expect
  {:successful-login? true
   :redirect-uri      default-redirect-uri
   :login-attributes  (some-saml-attributes "rasta")}
  (with-saml-default-setup
    (let [req-options (saml-post-request-options (saml-test-response)
                                                 (#'saml/encrypt-redirect-str default-redirect-uri))
          response (client-full-response :post 302 "/auth/sso" req-options)]
      {:successful-login? (successful-login? response)
       :redirect-uri      (get-in response [:headers "Location"])
       :login-attributes  (saml-login-attributes "rasta@metabase.com")})))

;; Test that if the RelayState is not set or is invalid, you are redirected back to the home page rather than failing
;; the entire login
(expect
  {:successful-login? true
   :redirect-uri      (public-settings/site-url)
   :login-attributes  (some-saml-attributes "rasta")}
  (with-saml-default-setup
    (let [req-options (saml-post-request-options (saml-test-response)
                                                 (str (#'saml/encrypt-redirect-str default-redirect-uri)
                                                      "something-random"))
          response (client-full-response :post 302 "/auth/sso" req-options)]
      {:successful-login? (successful-login? response)
       :redirect-uri      (get-in response [:headers "Location"])
       :login-attributes  (saml-login-attributes "rasta@metabase.com")})))

;; A new account will be created for a SAML user we haven't seen before
(expect
  {:new-user-exists-before? false
   :successful-login?       true
   :new-user                [{:email       "newuser@metabase.com", :first_name   "New", :last_login false,
                              :is_qbnewb   true,                   :is_superuser false, :id         true, :last_name "User",
                              :date_joined true,                   :common_name  "New User"}]
   :login-attributes        (some-saml-attributes "newuser")}
  (with-saml-default-setup
    (try
      (let [new-user-exists? (boolean (seq (db/select User :email "newuser@metabase.com")))
            req-options      (saml-post-request-options (new-user-saml-test-response)
                                                        (#'saml/encrypt-redirect-str default-redirect-uri))
            response         (client-full-response :post 302 "/auth/sso" req-options)]
        {:new-user-exists-before? new-user-exists?
         :successful-login?       (successful-login? response)
         :new-user                (tu/boolean-ids-and-timestamps (db/select User :email "newuser@metabase.com"))
         :login-attributes        (saml-login-attributes "newuser@metabase.com")})
      (finally
        (db/delete! User :email "newuser@metabase.com")))))

(defn- group-memberships [user-or-id]
  (when-let [group-ids (seq (db/select-field :group_id PermissionsGroupMembership :user_id (u/get-id user-or-id)))]
    (db/select-field :name PermissionsGroup :id [:in group-ids])))

(deftest login-should-sync-single-group-membership
  (testing "saml group sync works when there's just a single group, which gets interpreted as a string"
    (with-saml-default-setup
      (tt/with-temp* [PermissionsGroup [group-1 {:name (str ::group-1)}]]
        (tu/with-temporary-setting-values [saml-group-sync      true
                                           saml-group-mappings  {"group_1" [(u/get-id group-1)]}
                                           saml-attribute-group "GroupMembership"]
          (try
            ;; user doesn't exist until SAML request
            (is (not (db/select-one-id User :email "newuser@metabase.com")))
            (let [req-options            (saml-post-request-options (new-user-with-single-group-saml-test-response)
                                                                    (#'saml/encrypt-redirect-str default-redirect-uri))
                  response               (client-full-response :post 302 "/auth/sso" req-options)
                  new-user-id            (db/select-one-id User :email "newuser@metabase.com")]
              (assert (successful-login? response))
              (is (= #{"All Users"
                       ":metabase.mt.integrations.saml-test/group-1"}
                     (group-memberships new-user-id))))
            (finally
              (db/delete! User :email "newuser@metabase.com"))))))))

(deftest login-should-sync-multiple-group-membership
  (testing "saml group sync works when there are multiple groups, which gets interpreted as a list of stringss"
    (with-saml-default-setup
      (tt/with-temp* [PermissionsGroup [group-1 {:name (str ::group-1)}]
                      PermissionsGroup [group-2 {:name (str ::group-2)}]]
        (tu/with-temporary-setting-values [saml-group-sync      true
                                           saml-group-mappings  {"group_1" [(u/get-id group-1)]
                                                                 "group_2" [(u/get-id group-2)]}
                                           saml-attribute-group "GroupMembership"]
          (try
            ;; user doesn't exist until SAML request
            (is (not (db/select-one-id User :email "newuser@metabase.com")))
            (let [req-options            (saml-post-request-options (new-user-with-groups-saml-test-response)
                                                                    (#'saml/encrypt-redirect-str default-redirect-uri))
                  response               (client-full-response :post 302 "/auth/sso" req-options)
                  new-user-id            (db/select-one-id User :email "newuser@metabase.com")]
              (assert (successful-login? response))
              (is (= #{"All Users"
                       ":metabase.mt.integrations.saml-test/group-1"
                       ":metabase.mt.integrations.saml-test/group-2"}
                     (group-memberships new-user-id))))
            (finally
              (db/delete! User :email "newuser@metabase.com"))))))))
