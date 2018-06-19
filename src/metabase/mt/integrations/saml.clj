(ns metabase.mt.integrations.saml
  (:require [metabase.api.session :as session]
            [metabase.email.messages :as email]
            [metabase.models
             [setting :as setting :refer [defsetting]]
             [user :refer [User]]]
            [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]
            [toucan.db :as db]))

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

;; TODO - refactor the `core_user` model and the construction functions to get some reuse here
(defn- create-new-saml-auth-user!
  "This function is basically the same thing as the `create-new-google-auth-user` from `metabase.models.user`. We need
  to refactor the `core_user` table structure and the function used to populate it so that the enterprise product can
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

(defn- fetch-and-update-login-attributes! [email new-user-attributes]
  (when-let [{:keys [id login_attributes] :as user} (db/select-one User :email email)]
    (if (= login_attributes new-user-attributes)
      user
      (do
        (db/update! User id :login_attributes new-user-attributes)
        (User id)))))

(defn saml-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]
  (when-let [user (or (fetch-and-update-login-attributes! email user-attributes)
                      (create-new-saml-auth-user! first-name last-name email))]
    {:id (session/create-session! user)}))
