(ns metabase.mt.api.saml
  (:require [compojure.core :refer [GET POST]]
            [medley.core :as m]
            [metabase.api.common :as api]
            [metabase.mt.integrations.saml :as isaml]
            [ring.util.response :as resp]
            [saml20-clj
             [routes :as saml-routes]
             [shared :as saml-shared]
             [sp :as saml-sp]]))

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
   (let [keystore-file     (isaml/keystore-path)
         keystore-password (isaml/keystore-password)
         key-alias         (isaml/keystore-alias)
         decrypter         (saml-sp/make-saml-decrypter keystore-file keystore-password key-alias)
         sp-cert           (saml-shared/get-certificate-b64 keystore-file keystore-password key-alias)
         mutables          (assoc (saml-sp/generate-mutables)
                             :xml-signer (saml-sp/make-saml-signer keystore-file keystore-password key-alias))
         acs-uri           (str (isaml/base-uri) "/api/mt/saml")
         saml-req-factory! (saml-sp/create-request-factory mutables
                                                           (isaml/identity-provider-uri)
                                                           saml-routes/saml-format
                                                           (isaml/application-name)
                                                           acs-uri)
         prune-fn!         (partial saml-sp/prune-timed-out-ids!
                                    (:saml-id-timeouts mutables))]
     {:mutables           mutables
      :saml-req-factory!  saml-req-factory!
      :timeout-pruner-fn! prune-fn!
      :certificate-x509   sp-cert
      :acs-uri            acs-uri
      :decrypter          decrypter})))

(api/defendpoint GET "/meta"
  "Endpoint that gets SAML metadata"
  []
  (let [{:keys [acs-uri certificate-x509]} @saml-state]
    {:status 200
     :headers {"Content-type" "text/xml"}
     :body (saml-sp/metadata (isaml/application-name) acs-uri certificate-x509)}))

(api/defendpoint GET "/"
  "Initial call that will result in a redirect to the IDP along with information about how the IDP can authenticate
  and redirect them back to us"
  [:as req]
  (let [idp-uri (isaml/identity-provider-uri)
        {:keys [saml-req-factory! mutables]} @saml-state
        saml-request (saml-req-factory!)
        ;; We don't really use RelayState. It's intended to be something like an identifier from the Service Provider
        ;; (that's us) that we apss to the Identity Provider (i.e. Auth0) and it will include that state in it's
        ;; response. The IDP treats it as an opaque string and just returns it without examining/changing it
        hmac-relay-state (saml-routes/create-hmac-relay-state (:secret-key-spec mutables) "no-op")]
    (saml-sp/get-idp-redirect idp-uri saml-request hmac-relay-state)))

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
        idp-cert                          (isaml/identity-provider-certificate)
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
      (let [email               (get attrs (isaml/email-attribute))
            first-name          (get attrs (isaml/first-name-attribute) "Unknown")
            last-name           (get attrs (isaml/last-name-attribute) "Unknown")
            {session-token :id} (isaml/saml-auth-fetch-or-create-user! first-name last-name email attrs)]
        ;; TODO - FIXME
        #_(assoc (resp/redirect "/")
            :cookies {:x-metabase-session {:value session-token
                                           :path  "/"}})
        (resp/set-cookie (resp/redirect "/")
                         "metabase.SESSION_ID" session-token
                         {:path "/"}))
      {:status 500
       :body   "The SAML response from IdP does not validate!"})))

(api/define-routes)
