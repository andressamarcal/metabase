(ns metabase.mt.integrations.saml
  (:require [clojure.string :as str]
            [metabase.api.session :as session]
            [metabase.api.common :as api]
            [metabase.email.messages :as email]
            [medley.core :as m]
            [metabase.models
             [setting :as setting :refer [defsetting]]
             [user :refer [User]]]
            [metabase.mt.api.saml :as api-saml]
            [metabase.public-settings :as public-settings]
            [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]
            [metabase.mt.integrations.sso-settings :as sso-settings]
            [saml20-clj
             [routes :as saml-routes]
             [shared :as saml-shared]
             [sp :as saml-sp]]
            [ring.util.response :as resp]
            [toucan.db :as db]
            [metabase.mt.integrations.sso-utils :as sso-utils]))

(defn saml-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]

  (when-not (sso-settings/saml-configured?)
    (throw (IllegalArgumentException. "Can't create new SAML user when SAML is not configured")))

  (when-let [user (or (sso-utils/fetch-and-update-login-attributes! email user-attributes)
                      (sso-utils/create-new-sso-user! {:first_name first-name
                                                       :last_name  last-name
                                                       :email      email
                                                       :sso_source "saml"}))]
    {:id (session/create-session! user)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; SAML route supporting functions
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private saml-state
  (delay
   (let [keystore-file     (sso-settings/saml-keystore-path)
         keystore-password (sso-settings/saml-keystore-password)
         key-alias         (sso-settings/saml-keystore-alias)
         decrypter         (saml-sp/make-saml-decrypter keystore-file keystore-password key-alias)
         sp-cert           (saml-shared/get-certificate-b64 keystore-file keystore-password key-alias)
         mutables          (assoc (saml-sp/generate-mutables)
                             :xml-signer (saml-sp/make-saml-signer keystore-file keystore-password key-alias))
         acs-uri           (str (public-settings/site-url) "/auth/sso")
         saml-req-factory! (saml-sp/create-request-factory mutables
                                                           (sso-settings/saml-identity-provider-uri)
                                                           saml-routes/saml-format
                                                           (sso-settings/saml-application-name)
                                                           acs-uri)
         prune-fn!         (partial saml-sp/prune-timed-out-ids!
                                    (:saml-id-timeouts mutables))]

     {
      ;; `mutables` is a bundle of mutable data needed by the SAML library. It has things like an id counter to track
      ;; number of messages sent etc
      :mutables           mutables
      ;; Also required for making SAML requests, used for creating SAML requests
      :saml-req-factory!  saml-req-factory!
      ;; Used for timing out old SAML requests
      :timeout-pruner-fn! prune-fn!
      ;; Our certificate, optional, but will validate the integrity of the data returned
      :certificate-x509   sp-cert
      ;; URL that the SAML IDP (i.e. Auth0) will redirect the user to upon successful login
      :acs-uri            acs-uri
      ;; Uses `sp-cert` to decrypt valid responses from the SAML IDP
      :decrypter          decrypter})))

(defn get-idp-redirect
  "This is similar to `saml/get-idp-redirect` but allows existing parameters on the `idp-url`. The original version
  assumed no paramters and always included a `?` before the SAML parameters. This version will check for a `?` and if
  present just append the parameters.

  Returns Ring response for HTTP 302 redirect."
  [idp-url saml-request relay-state]
  (resp/redirect
   (str idp-url
        (if (str/includes? idp-url "?")
          "&"
          "?")
        (let [saml-request (saml-shared/str->deflate->base64 saml-request)]
          (saml-shared/uri-query-str
           {:SAMLRequest saml-request :RelayState relay-state})))))

(defn- check-saml-enabled []
  (api/check (sso-settings/saml-configured?)
    [400 (tru "SAML has not been enabled and/or configured")]))

(defn- encrypt-redirect-str [redirect]
  (-> @saml-state
      (get-in [:mutables :secret-key-spec])
      (saml-routes/create-hmac-relay-state redirect)))

(defmethod api-saml/sso-get :saml
  ;; Initial call that will result in a redirect to the IDP along with information about how the IDP can authenticate
  ;; and redirect them back to us
  [req]
  (check-saml-enabled)
  (let [redirect                    (get-in req [:params :redirect])
        idp-uri                     (sso-settings/saml-identity-provider-uri)
        {:keys [saml-req-factory!]} @saml-state
        saml-request                (saml-req-factory!)
        hmac-relay-state            (encrypt-redirect-str redirect)]
    (get-idp-redirect idp-uri saml-request hmac-relay-state)))

(defn- unwrap-user-attributes
  "For some reason all of the user attributes coming back from the saml library are wrapped in a list, instead of 'Ryan',
  it's ('Ryan'). This function discards the list if there's just a single item in it."
  [m]
  (m/map-vals (fn [maybe-coll]
                (if (and (coll? maybe-coll)
                         (= 1 (count maybe-coll)))
                  (first maybe-coll)
                  maybe-coll))
              m))

(defn- decrypt-relay-state [relay-state]
  (-> @saml-state
      (get-in [:mutables :secret-key-spec])
      (saml-routes/valid-hmac-relay-state? relay-state)))

(defmethod api-saml/sso-post :saml
  ;; Does the verification of the IDP's response and 'logs the user in'. The attributes are available in the response:
  ;; `(get-in saml-info [:assertions :attrs])
  [{params :params session :session}]
  (check-saml-enabled)
  (let [{:keys [decrypter]}               @saml-state
        idp-cert                          (sso-settings/saml-identity-provider-certificate)
        xml-string                        (saml-shared/base64->inflate->str (:SAMLResponse params))
        [valid-relay-state? continue-url] (decrypt-relay-state (:RelayState params))
        saml-resp                         (saml-sp/xml-string->saml-resp xml-string)
        valid-signature?                  (if idp-cert
                                            (saml-sp/validate-saml-response-signature saml-resp idp-cert)
                                            false)
        valid?                            (and valid-relay-state? valid-signature?)
        saml-info                         (when valid? (saml-sp/saml-resp->assertions saml-resp decrypter))]
    (if-let [attrs (and valid? (-> saml-info :assertions first :attrs unwrap-user-attributes))]
      (let [email               (get attrs (sso-settings/saml-attribute-email))
            first-name          (get attrs (sso-settings/saml-attribute-firstname) "Unknown")
            last-name           (get attrs (sso-settings/saml-attribute-lastname) "Unknown")
            {session-token :id} (saml-auth-fetch-or-create-user! first-name last-name email attrs)]
        (resp/set-cookie (resp/redirect continue-url)
                         "metabase.SESSION_ID" session-token
                         {:path "/"}))
      {:status 500
       :body   "The SAML response from IdP does not validate!"})))
