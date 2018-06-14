(ns metabase.mt.api.saml
  (:require [clojure.string :as str]
            [compojure.core :refer [GET POST]]
            [medley.core :as m]
            [metabase.api.common :as api]
            [metabase.mt.integrations.saml :as isaml]
            [metabase.public-settings :as public-settings]
            [ring.util.response :as resp]
            [saml20-clj
             [routes :as saml-routes]
             [shared :as saml-shared]
             [sp :as saml-sp]]))

(def ^:private saml-state
  (delay
   (let [keystore-file     (isaml/saml-keystore-path)
         keystore-password (isaml/saml-keystore-password)
         key-alias         (isaml/saml-keystore-alias)
         decrypter         (saml-sp/make-saml-decrypter keystore-file keystore-password key-alias)
         sp-cert           (saml-shared/get-certificate-b64 keystore-file keystore-password key-alias)
         mutables          (assoc (saml-sp/generate-mutables)
                             :xml-signer (saml-sp/make-saml-signer keystore-file keystore-password key-alias))
         acs-uri           (str (public-settings/site-url) "/api/mt/saml")
         saml-req-factory! (saml-sp/create-request-factory mutables
                                                           (isaml/saml-identity-provider-uri)
                                                           saml-routes/saml-format
                                                           (isaml/saml-application-name)
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

(api/defendpoint GET "/meta"
  "Endpoint that gets SAML metadata. Not sure if this has any real value to us, but is included in SAML demo/tutorial"
  []
  (let [{:keys [acs-uri certificate-x509]} @saml-state]
    {:status 200
     :headers {"Content-type" "text/xml"}
     :body (saml-sp/metadata (isaml/saml-application-name) acs-uri certificate-x509)}))

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

(api/defendpoint GET "/"
  "Initial call that will result in a redirect to the IDP along with information about how the IDP can authenticate
  and redirect them back to us"
  [:as req]
  (let [idp-uri (isaml/saml-identity-provider-uri)
        {:keys [saml-req-factory! mutables]} @saml-state
        saml-request (saml-req-factory!)
        ;; We don't really use RelayState. It's intended to be something like an identifier from the Service Provider
        ;; (that's us) that we pass to the Identity Provider (i.e. Auth0) and it will include that state in it's
        ;; response. The IDP treats it as an opaque string and just returns it without examining/changing it
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec mutables) "no-op")]
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

(api/defendpoint POST "/"
  "Does the verification of the IDP's response and 'logs the user in'. The attributes are available in the
  response: `(get-in saml-info [:assertions :attrs])"
  {params :params session :session}
  (let [{:keys [saml-req-factory!
                mutables, decrypter]}     @saml-state
        idp-cert                          (isaml/saml-identity-provider-certificate)
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
      (let [email               (get attrs (isaml/saml-attribute-email))
            first-name          (get attrs (isaml/saml-attribute-firstname) "Unknown")
            last-name           (get attrs (isaml/saml-attribute-lastname) "Unknown")
            {session-token :id} (isaml/saml-auth-fetch-or-create-user! first-name last-name email attrs)]
        (resp/set-cookie (resp/redirect "/")
                         "metabase.SESSION_ID" session-token
                         {:path "/"}))
      {:status 500
       :body   "The SAML response from IdP does not validate!"})))

(api/define-routes)
