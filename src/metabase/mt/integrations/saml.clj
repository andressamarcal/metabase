(ns metabase.mt.integrations.saml
  (:require [metabase.api.session :as session]
            [metabase.email.messages :as email]
            [metabase.models
             [setting :as setting :refer [defsetting]]
             [user :refer [User]]]
            [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]
            [toucan.db :as db]))

(defsetting identity-provider-uri
  (tru "This is a URI if of the SAML Identity Provider (where the user would login)"))

(defsetting base-uri
  (str (tru "This is the root URL for Metabase.")
       (tru "This will be used to redirect the user after a successful login."))
  :default "http://localhost:3000")

(defsetting application-name
  (tru "This application name will be used for requests to the Identity Provider")
  :default "Metabase")

(defsetting identity-provider-certificate
  (tru "Encoded certificate for the identity provider"))

(defsetting keystore-path
  (tru "Absolute path to the Keystore file to use for signing SAML requests"))

(defsetting keystore-password
  (tru "Password for opening the keystore")
  :default "changeit")

(defsetting keystore-alias
  (tru "Alias for the key that Metabase should use for signing SAML requests")
  :default "metabase")

(defsetting email-attribute
  (tru "SAML attribute for the user''s email address")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress")

(defsetting first-name-attribute
  (tru "SAML attribute for the user''s first name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname")

(defsetting last-name-attribute
  (tru "SAML attribute for the user''s last name")
  :default "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname")

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
