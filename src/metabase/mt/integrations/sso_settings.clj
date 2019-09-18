(ns metabase.mt.integrations.sso-settings
  "Namesapce for defining settings used by the SSO backends. This is separate as both the functions needed to support
  the SSO backends and the generic routing code used to determine which SSO backend to use need this
  information. Separating out this information creates a better dependency graph and avoids circular dependencies."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metabase.models.setting :as setting :refer [defsetting]]
            [metabase.util :as u]
            [metabase.util
             [i18n :refer [deferred-tru trs tru]]
             [schema :as su]]
            [saml20-clj.shared :as saml20.shared]
            [schema.core :as s]))

(def ^:private GroupMappings
  (s/maybe {su/KeywordOrString [su/IntGreaterThanZero]}))

(def ^:private ^{:arglists '([group-mappings])} validate-group-mappings
  (s/validator GroupMappings))

(defsetting saml-enabled
  (deferred-tru "Enable SAML authentication.")
  :type    :boolean
  :default false)

(defsetting saml-identity-provider-uri
  (deferred-tru "This is a URI if of the SAML Identity Provider (where the user would login)"))

(s/defn ^:private validate-saml-idp-cert
  "Validate that an encoded identity provider certificate is valid, or throw an Exception."
  [idp-cert-str :- s/Str]
  (try
    (-> idp-cert-str saml20.shared/certificate-x509 saml20.shared/jcert->public-key)
    (catch Throwable e
      (log/error e (trs "Error parsing SAML identity provider certificate"))
      (throw
       (Exception. (str/join
                    " "
                    [(tru "Invalid identity provider certificate. Certificate should be a base-64 encoded string.")
                     (tru "Do NOT include ASCII armor lines like `-----BEGIN CERTIFICATE-----`.")]))))))

(defsetting saml-identity-provider-certificate
  (deferred-tru "Encoded certificate for the identity provider")
  :setter (fn [new-value]
            ;; when setting the idp cert validate that it's something we
            (when new-value
              (validate-saml-idp-cert new-value))
            (setting/set-string! :saml-identity-provider-certificate new-value)))

(defsetting saml-application-name
  (deferred-tru "This application name will be used for requests to the Identity Provider")
  :default "Metabase")

(defsetting saml-keystore-path
  (deferred-tru "Absolute path to the Keystore file to use for signing SAML requests"))

(defsetting saml-keystore-password
  (deferred-tru "Password for opening the keystore")
  :default "changeit")

(defsetting saml-keystore-alias
  (deferred-tru "Alias for the key that Metabase should use for signing SAML requests")
  :default "metabase")

(defsetting saml-attribute-email
  (deferred-tru "SAML attribute for the user''s email address")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")

(defsetting saml-attribute-firstname
  (deferred-tru "SAML attribute for the user''s first name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname")

(defsetting saml-attribute-lastname
  (deferred-tru "SAML attribute for the user''s last name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname")

(defsetting saml-group-sync
  (deferred-tru "Enable group membership synchronization with SAML.")
  :type    :boolean
  :default false)

(defsetting saml-attribute-group
  (deferred-tru "SAML attribute for group syncing")
  :default "member_of")

(defsetting saml-group-mappings
  ;; Should be in the form: {"groupName": [1, 2, 3]} where keys are SAML groups and values are lists of MB groups IDs
  (deferred-tru "JSON containing SAML to Metabase group mappings.")
  :type    :json
  :default {}
  :setter (comp (partial setting/set-json! :saml-group-mappings) validate-group-mappings))

(defn saml-configured?
  "Check if SAML is enabled and that the mandatory settings are configured."
  []
  (boolean (and (saml-enabled)
                (saml-identity-provider-uri)
                (saml-identity-provider-certificate))))

(defsetting jwt-enabled
  (deferred-tru "Enable JWT based authentication")
  :type    :boolean
  :default false)

(defsetting jwt-identity-provider-uri
  (deferred-tru "URL of JWT based login page"))

(defsetting jwt-shared-secret
  (deferred-tru "String used to seed the private key used to validate JWT messages")
  :setter (fn [new-value]
            (when (seq new-value)
              (assert (u/hexadecimal-string? new-value)
                       "Invalid JWT Shared Secret key must be a hexadecimal-encoded 256-bit key (i.e., a 64-character string)."))
            (setting/set-string! :jwt-shared-secret new-value)))

(defsetting jwt-attribute-email
  (deferred-tru "Key to retrieve the JWT user's email address")
  :default "email")

(defsetting jwt-attribute-firstname
  (deferred-tru "Key to retrieve the JWT user's first name")
  :default "first_name")

(defsetting jwt-attribute-lastname
  (deferred-tru "Key to retrieve the JWT user's last name")
  :default "last_name")

(defsetting jwt-attribute-groups
  (deferred-tru "Key to retrieve the JWT user's groups")
  :default "groups")

(defsetting jwt-group-sync
  (deferred-tru "Enable group membership synchronization with JWT.")
  :type    :boolean
  :default false)

(defsetting jwt-group-mappings
  ;; Should be in the form: {"groupName": [1, 2, 3]} where keys are JWT groups and values are lists of MB groups IDs
  (deferred-tru "JSON containing JWT to Metabase group mappings.")
  :type    :json
  :default {}
  :setter  (comp (partial setting/set-json! :jwt-group-mappings) validate-group-mappings))

(defn jwt-configured?
  "Check if JWT is enabled and that the mandatory settings are configured."
  []
  (boolean (and (jwt-enabled)
                (jwt-identity-provider-uri)
                (jwt-shared-secret))))
