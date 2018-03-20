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
            [schema.core :as s]
            [saml20-clj
             [routes :as saml-routes]
             [sp :as saml-sp]
             [shared :as saml-shared]]
            [toucan.db :as db]))

(def ^:private config
  {:app-name "Test SAML app"
   :base-uri "http://localhost:3000"
   :idp-uri "http://localhost:7000"
   :idp-cert "MIIEMDCCAxigAwIBAgIJAPZcPVjOJlnMMA0GCSqGSIb3DQEBBQUAMG0xCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1TYW4gRnJhbmNpc2NvMRAwDgYDVQQKEwdKYW5reUNvMR8wHQYDVQQDExZUZXN0IElkZW50aXR5IFByb3ZpZGVyMB4XDTE3MDMxMjE5MjkzNFoXDTM3MDMwNzE5MjkzNFowbTELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcTDVNhbiBGcmFuY2lzY28xEDAOBgNVBAoTB0phbmt5Q28xHzAdBgNVBAMTFlRlc3QgSWRlbnRpdHkgUHJvdmlkZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC02ay1lhBNntX3p+8Wlsq0wlq31rJkBkmlz/CAJz1kQmhwBxpc0icLHdcWwF4BJ3zcV/CtpNpWv8uD+7EPVJKpavbdi318Ug2K2vqZI7Dyvqi3X50+s3uyAG/gIy63h1qwfFOdIVj7YbJiKvUF4/6w4BWbnNgQL3dBNOPEGdzNgzRlffIbRJwDaIpgiY8nE8sAkTmPzaAuKAGc5eedYDZW+xkDHHHFHtpCxnAaDVfwCPxv+uGRP7emSp7TvFHFKmKQUTZh+FYyJfk8yUs6AJTaJOR2mRFrFxht7pRlavWeAbRDaa4wUUgwHNqvjBI47RZnOeplljN4TyLOL9J9tvXJAgMBAAGjgdIwgc8wHQYDVR0OBBYEFD9Q9rcVIp+s4unYzstD/T5hZjNHMIGfBgNVHSMEgZcwgZSAFD9Q9rcVIp+s4unYzstD/T5hZjNHoXGkbzBtMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNU2FuIEZyYW5jaXNjbzEQMA4GA1UEChMHSmFua3lDbzEfMB0GA1UEAxMWVGVzdCBJZGVudGl0eSBQcm92aWRlcoIJAPZcPVjOJlnMMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQADggEBAJDIaTj9PimXTExXGLAU7p8HzACvXXmO7fMZWxahvWDwrgmlhJ+7S5oYYqkYkCleBtYbHduakGPeWbEwcZjNzLsMFYxQntiM3gz1dUS3DpR5lP+/UhO7HNN81XSgj63K56YO1iS8hX6NzLylOcfxw3wgMtguzIyxLLGfE1rbZkC67D/kfz+jLp6U9ChjqHE3FbSFrl2dhHltdHJ4ErM1vlbTIoa+52ka4WKD2uArgilgTo7wvG1fE9n7SXcK5QdUlb4Voi71UchkkauuNXhq090i6Y6GP1EicHuK6ymT4P9sGwnGoXHPik9v6QuTwxcAZIoR9J6P8CyrL2OHIseh6xQ="
   :keystore-file "keystore.jks"
   :keystore-password "changeit"
   :key-alias "mylocalsp"})

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
     :body (saml-sp/metadata (:app-name config) acs-uri certificate-x509)}))

(api/defendpoint GET "/"
  "Initial call that will result in a redirect to the IDP along with information about how the IDP can authenticate
  and redirect them back to us"
  [:as req]
  (let [{:keys [idp-uri]} config
        {:keys [saml-req-factory! mutables]} @saml-state
        saml-request (saml-req-factory!)
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec mutables) "http://www.google.com")]
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
    (if-let [{:strs [firstName lastName email]
              :as   attrs}                     (and valid? (-> saml-info :assertions first :attrs unwrap-user-attributes))]
      (saml-auth-fetch-or-create-user! firstName lastName email attrs)
      {:status 500
       :body   "The SAML response from IdP does not validate!"})))

(api/define-routes)
