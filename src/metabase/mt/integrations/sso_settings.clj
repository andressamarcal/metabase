(ns metabase.mt.integrations.sso-settings
  "Namesapce for defining settings used by the SSO backends. This is separate as both the functions needed to support
  the SSO backends and the generic routing code used to determine which SSO backend to use need this
  information. Separating out this information creates a better dependency graph and avoids circular dependencies."
  (:require [metabase.models.setting :as setting :refer [defsetting]]
            [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]))

(defsetting saml-enabled
  (tru "Enable SAML authentication.")
  :type    :boolean
  :default false)

(defsetting saml-identity-provider-uri
  (tru "This is a URI if of the SAML Identity Provider (where the user would login)"))

(defsetting saml-identity-provider-certificate
  (tru "Encoded certificate for the identity provider"))

(defsetting saml-application-name
  (tru "This application name will be used for requests to the Identity Provider")
  :default "Metabase")

(defsetting saml-keystore-path
  (tru "Absolute path to the Keystore file to use for signing SAML requests"))

(defsetting saml-keystore-password
  (tru "Password for opening the keystore")
  :default "changeit")

(defsetting saml-keystore-alias
  (tru "Alias for the key that Metabase should use for signing SAML requests")
  :default "metabase")

(defsetting saml-attribute-email
  (tru "SAML attribute for the user''s email address")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")

(defsetting saml-attribute-firstname
  (tru "SAML attribute for the user''s first name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname")

(defsetting saml-attribute-lastname
  (tru "SAML attribute for the user''s last name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname")

(defn saml-configured?
  "Check if SAML is enabled and that the mandatory settings are configured."
  []
  (boolean (and (saml-enabled)
                (saml-identity-provider-uri)
                (saml-identity-provider-certificate))))

(defsetting jwt-enabled
  (tru "Enable JWT based authentication")
  :type    :boolean
  :default false)

(defsetting jwt-identity-provider-uri
  (tru "URL of JWT based login page"))

(defsetting jwt-shared-secret
  (tru "String used to seed the private key used to validate JWT messages")
  :setter (fn [new-value]
            (when (seq new-value)
              (assert (u/hexidecimal-string? new-value)
                       "Invalid JWT Shared Secret key must be a hexadecimal-encoded 256-bit key (i.e., a 64-character string)."))
            (setting/set-string! :jwt-shared-secret new-value)))

(defsetting jwt-attribute-email
  (tru "Key to retrieve the JWT user's email address")
  :default "email")

(defsetting jwt-attribute-firstname
  (tru "Key to retrieve the JWT user's first name")
  :default "first_name")

(defsetting jwt-attribute-lastname
  (tru "Key to retrieve the JWT user's last name")
  :default "last_name")

(defn jwt-configured?
  "Check if JWT is enabled and that the mandatory settings are configured."
  []
  (boolean (and (jwt-enabled)
                (jwt-identity-provider-uri)
                (jwt-shared-secret))))
