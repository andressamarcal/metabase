(ns metabase.mt.api.saml
  (:require [compojure.core :refer [DELETE GET POST PUT]]
            [medley.core :as m]
            [metabase.api
             [common :as api]
             [session :as session]]
            [metabase.email.messages :as email]
            [metabase.models.user :refer [User]]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [ring.util.response :as resp]
            [schema.core :as s]
            [saml20-clj
             [routes :as saml-routes]
             [sp :as saml-sp]
             [shared :as saml-shared]]
            [toucan.db :as db]))

(def ^:private config
  {:app-name "Test SAML app"
   :base-uri "http://localhost:3000"
   :idp-uri "https://saml-metabase-test.auth0.com/samlp/W1R5HozFA9cnbFVbmIT8KPdVmH0pU85k"
   :idp-cert "MIIDEzCCAfugAwIBAgIJYpjQiNMYxf1GMA0GCSqGSIb3DQEBCwUAMCcxJTAjBgNV
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
g9oYBkdxlhK9zZvkjCgaLCen+0aY67A="
;;   :keystore-file "keystore.jks"
;;   :keystore-password "changeit"
;;   :key-alias "mylocalsp"
   })

;; The SP routes. They can be combined with application specific routes. Also it is assumed that
;; they are wrapped with compojure.handler/site or wrap-params and wrap-session.
;; The single argument is a map containing the following fields:
;; :app-name - The application's name
;; :base-uri - The Base URI for the application i.e. its remotely accessible hostname and
;;             (if needed) port, e.g. https://example.org:8443 This is used for building the
;;             'AssertionConsumerService' URI for the HTTP-POST Binding, by prepending the
;;             base-uri to the '/saml' string.
;; :idp-uri  - The URI for the IdP to use. This should be the URI for the HTTP-Redirect SAML Binding
;; :idp-cert - The IdP certificate that contains the public key used by IdP for signing responses.
;;             This is optional: if not used no signature validation will be performed in the responses
;; :keystore-file - The filename that is the Java keystore for the private key used by this SP for the
;;                  decryption of responses coming from IdP
;; :keystore-password - The password for opening the keystore file
;; :key-alias - The alias for the private key in the keystore
;; The created routes are the following:
;; - GET /saml/meta : This returns a SAML metadata XML file that has the needed information
;;                    for registering this SP. For example, it has the public key for this SP.
;; - GET /saml : it redirects to the IdP with the SAML request envcoded in the URI per the
;;               HTTP-Redirect binding. This route accepts a 'continue' parameter that can
;;               have the relative URI, where the browser should be redirected to after the
;;               successful login in the IdP.
;; - POST /saml : this is the endpoint for accepting the responses from the IdP. It then redirects
;;                the browser to the 'continue-url' that is found in the RelayState paramete, or the '/' root
;;                of the app.

(def ^:private saml-state
  (delay
   (let [{:keys [key-alias keystore-password keystore-file base-uri app-name idp-uri]} config
         decrypter (saml-sp/make-saml-decrypter keystore-file keystore-password key-alias)
         sp-cert (saml-shared/get-certificate-b64 keystore-file keystore-password key-alias)
         mutables (assoc (saml-sp/generate-mutables)
                    :xml-signer (saml-sp/make-saml-signer keystore-file keystore-password key-alias))

         acs-uri (str base-uri "/api/mt/saml")
         saml-req-factory! (saml-sp/create-request-factory mutables
                                                           idp-uri
                                                           saml-routes/saml-format
                                                           app-name
                                                           acs-uri)
         prune-fn! (partial saml-sp/prune-timed-out-ids!
                            (:saml-id-timeouts mutables))]
     {:mutables mutables
      :saml-req-factory! saml-req-factory!
      :timeout-pruner-fn! prune-fn!
      :certificate-x509 sp-cert
      :acs-uri acs-uri
      :decrypter decrypter})))

;; TODO - refactor the `core_user` model and the construction functions to get some reuse here
(defn- create-new-saml-auth-user!
  "This function is basically the same thing as the `create-new-google-auth-user` from `metabase.models.user`. We need
  to refactor the `core_user` table structure and the function used to populate it so that the enterprise produce can
  reuse it"
  [first-name last-name email]
  {:pre [(string? first-name) (string? last-name) (u/email? email)]}
  (session/check-autocreate-user-allowed-for-email email)
  (u/prog1 (db/insert! User
             :email      email
             :first_name first-name
             :last_name  last-name
             :password   (str (java.util.UUID/randomUUID))
             :saml_auth  true)
    ;; send an email to everyone including the site admin if that's set
    (email/send-user-joined-admin-notification-email! <>, :google-auth? true)))

(defn- fetch-and-update-login-attributes! [first-name last-name email old-user-attributes]
  (when-let [{:keys [id login_attributes] :as user} (db/select-one [User :id :last_login :login_attributes] :email email)]
    (if (= login_attributes old-user-attributes)
      user
      (do
        (db/update! User (:id user) :login_attributes login_attributes)
        (assoc user :login_attributes login_attributes)))))

(defn- saml-auth-fetch-or-create-user! [first-name last-name email user-attributes]
  (when-let [user (or (fetch-and-update-login-attributes! first-name last-name email user-attributes)
                      (create-new-saml-auth-user! first-name last-name email))]
    {:id (session/create-session! user)}))

(api/defendpoint GET "/meta"
  "Endpoint that gets SAML metadata"
  []
  (let [{:keys [acs-uri certificate-x509]} @saml-state]
    {:status 200
     :headers {"Content-type" "text/xml"}
     :body (slurp "/home/ryan/Downloads/saml-metabase-test_auth0_com-metadata.xml")
     #_(saml-sp/metadata (:app-name config) acs-uri certificate-x509)}))

(api/defendpoint GET "/"
  "Initial call that will result in a redirect to the IDP along with information about how the IDP can authenticate
  and redirect them back to us"
  [:as req]
  (let [{:keys [idp-uri]} config
        {:keys [saml-req-factory! mutables]} @saml-state
        saml-request (saml-req-factory!)
        ;; We don't really use RelayState. It's intended to be something like an identifier from the Service Provider
        ;; (that's us) that we apss to the Identity Provider (i.e. Auth0) and it will include that state in it's
        ;; response. The IDP treats it as an opaque string and just returns it without examining/changing it
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec mutables) "no-op")]
    (saml-sp/get-idp-redirect idp-uri saml-request hmac-relay-state)))

(defn- unwrap-user-attributes
  "For some reason all of the user attributes coming back from saml-test are wrapped in a list, instead of 'Ryan',
  it's ('Ryan'). This function discards the list if there's just a single item in it."
  [m]
  (m/map-vals (fn [maybe-coll]
                (if (and (coll? maybe-coll)
                         (= 1 (count maybe-coll)))
                  (first maybe-coll)
                  maybe-coll))
              m))

(def ^:private email-key
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")

(def ^:private first-name-key
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname")

(def ^:private last-name-key
  "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname")

(api/defendpoint POST "/"
  "Does the verification of the IDP's response and 'logs the user in'. The attributes are available in the
  response: `(get-in saml-info [:assertions :attrs])"
  {params :params session :session}
  (let [{:keys [saml-req-factory!
                mutables, decrypter]}     @saml-state
        {:keys [idp-cert]}                config
        xml-string                        (saml-shared/base64->inflate->str (:SAMLResponse params))
        relay-state                       (:RelayState params)
        [valid-relay-state? continue-url] (saml-routes/valid-hmac-relay-state? (:secret-key-spec mutables) relay-state)
        saml-resp                         (saml-sp/xml-string->saml-resp xml-string)
        valid-signature?                  (if idp-cert
                                            (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                                            false)
        valid?                            (and valid-relay-state? valid-signature?)
        saml-info                         (when valid? (saml-sp/saml-resp->assertions saml-resp decrypter))]
    (if-let [attrs (and valid? (-> saml-info :assertions first :attrs unwrap-user-attributes))]
      (do
        (println "All assertsion")
        (clojure.pprint/pprint (:assertions saml-info))
        (println "Attrs")
        (clojure.pprint/pprint attrs)
        (let [email (get attrs email-key)
              first-name (get attrs first-name-key "Unknown")
              last-name (get attrs last-name-key "Unknown")
              {session-token :id} (saml-auth-fetch-or-create-user! first-name last-name email attrs)]
          ;; TODO - FIXME
          #_(assoc (resp/redirect "/")
              :cookies {:x-metabase-session {:value session-token
                                             :path  "/"}})
          (resp/set-cookie (resp/redirect "/")
                           "metabase.SESSION_ID" session-token
                           {:path "/"})))
      {:status 500
       :body   "The SAML response from IdP does not validate!"})))

(api/define-routes)
