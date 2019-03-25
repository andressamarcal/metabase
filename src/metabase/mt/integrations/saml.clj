(ns metabase.mt.integrations.saml
  "Implementation of the SAML backend for SSO"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [medley.core :as m]
            [metabase
             [middleware :as middleware]
             [public-settings :as public-settings]
             [util :as u]]
            [metabase.api
             [common :as api]
             [session :as session]]
            [metabase.integrations.common :as integrations.common]
            [metabase.mt.api.sso :as sso]
            [metabase.mt.integrations
             [sso-settings :as sso-settings]
             [sso-utils :as sso-utils]]
            [puppetlabs.i18n.core :refer [trs tru]]
            [ring.util.response :as resp]
            [saml20-clj
             [routes :as saml-routes]
             [shared :as saml-shared]
             [sp :as saml-sp]]))

(defn- group-names->ids [group-names]
  (set (mapcat (sso-settings/saml-group-mappings)
               (map keyword group-names))))

(defn- sync-groups! [user group-names]
  (when (sso-settings/saml-group-sync)
    (when group-names
      (integrations.common/sync-group-memberships! user (group-names->ids group-names)))))

(defn saml-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email group-names user-attributes]
  (when-not (sso-settings/saml-configured?)
    (throw (IllegalArgumentException. "Can't create new SAML user when SAML is not configured")))

  (when-let [user (or (sso-utils/fetch-and-update-login-attributes! email user-attributes)
                      (sso-utils/create-new-sso-user! {:first_name       first-name
                                                       :last_name        last-name
                                                       :email            email
                                                       :sso_source       "saml"
                                                       :login_attributes user-attributes}))]
    (sync-groups! user group-names)
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
           {:SAMLRequest saml-request, :RelayState relay-state})))))

(defn- check-saml-enabled []
  (api/check (sso-settings/saml-configured?)
    [400 (tru "SAML has not been enabled and/or configured")]))

(defn- encrypt-redirect-str [redirect]
  (-> @saml-state
      (get-in [:mutables :secret-key-spec])
      (saml-routes/create-hmac-relay-state redirect)))

(defmethod sso/sso-get :saml
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

(defn- decrypt-relay-state
  "Decrypt `:RelayState` parameter if possible. Returns continue URL."
  [relay-state]
  (let [secret-key-spec                   (get-in @saml-state [:mutables :secret-key-spec])
        [valid-relay-state? continue-url] (saml-routes/valid-hmac-relay-state? secret-key-spec relay-state)]
    (when-not valid-relay-state?
      (log/error (trs "Unable to log in: RelayState is invalid. RelayState: {0}" (u/pprint-to-str 'red relay-state)))
      (throw (ex-info (str (tru "Unable to log in: RelayState is invalid."))
               {:status-code 500})))
    continue-url))

(defn- validate-signature [saml-response]
  (when-let [idp-cert (sso-settings/saml-identity-provider-certificate)]
    (when-not (saml-sp/validate-saml-response-signature saml-response idp-cert)
      (throw (ex-info (str (tru "Unable to log in: SAML response signature is invalid."))
               {:status-code 500})))))

(defn- xml-string->saml-response [xml-string]
  (u/prog1 (saml-sp/xml-string->saml-resp xml-string)
    (validate-signature <>)))

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

(defn- saml-response->attributes [saml-response]
  (let [{:keys [decrypter]} @saml-state
        saml-info           (saml-sp/saml-resp->assertions saml-response decrypter)
        attrs               (-> saml-info :assertions first :attrs unwrap-user-attributes)]
    (when-not attrs
      (throw (ex-info (str (tru "Unable to log in: SAML info does not contain user attributes."))
               {:status-code 500})))
    attrs))

(defmethod sso/sso-post :saml
  ;; Does the verification of the IDP's response and 'logs the user in'. The attributes are available in the response:
  ;; `(get-in saml-info [:assertions :attrs])
  [{:keys [params], :as request}]
  (check-saml-enabled)
  (let [continue-url        (u/ignore-exceptions (decrypt-relay-state (:RelayState params)))
        xml-string          (saml-shared/base64->inflate->str (:SAMLResponse params))
        saml-response       (xml-string->saml-response xml-string)
        attrs               (saml-response->attributes saml-response)
        email               (get attrs (sso-settings/saml-attribute-email))
        first-name          (get attrs (sso-settings/saml-attribute-firstname) "Unknown")
        last-name           (get attrs (sso-settings/saml-attribute-lastname) "Unknown")
        groups              (get attrs (sso-settings/saml-attribute-group))
        {session-token :id} (saml-auth-fetch-or-create-user! first-name last-name email groups attrs)]
    ;; TODO - use the new cookie response stuff in the session middleware once 32.0+ is merged in
    (resp/set-cookie
     (resp/redirect (or continue-url "http://localhost:3000/"))
     @#'middleware/metabase-session-cookie
     session-token
     {:path "/"})))
