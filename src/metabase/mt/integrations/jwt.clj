(ns metabase.mt.integrations.jwt
  (:require [buddy.sign.jwt :as jwt]
            [metabase.api.common :as api]
            [metabase.mt.integrations.sso-settings :as sso-settings]
            [metabase.mt.api.saml :as api-saml]
            [ring.util.response :as resp]
            [metabase.util :as u]
            [toucan.db :as db]
            [metabase.models.user :refer [User]]
            [metabase.email.messages :as email]
            [metabase.api.session :as session]
            [puppetlabs.i18n.core :refer [tru]]))


;; TODO - refactor the `core_user` model and the construction functions to get some reuse here
(defn- create-new-jwt-auth-user!
  "This function is basically the same thing as the `create-new-google-auth-user` from `metabase.models.user`. We need
  to refactor the `core_user` table structure and the function used to populate it so that the enterprise product can
  reuse it"
  [first-name last-name email]
  {:pre [(string? first-name) (string? last-name) (u/email? email)]}
  ;; Double checking the SAML support here, should not reach this point if SAML hasn't been configured properly
  (when-not (sso-settings/jwt-configured?)
    (throw (IllegalArgumentException. "Can't create new SAML user when SAML is not configured")))
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

(defn jwt-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]
  (when-let [user (or (fetch-and-update-login-attributes! email user-attributes)
                      (create-new-jwt-auth-user! first-name last-name email))]
    {:id (session/create-session! user)}))

(def ^:private jwt-attribute-email     (comp keyword sso-settings/jwt-attribute-email))
(def ^:private jwt-attribute-firstname (comp keyword sso-settings/jwt-attribute-firstname))
(def ^:private jwt-attribute-lastname  (comp keyword sso-settings/jwt-attribute-lastname))

(defn- jwt-data->login-attributes [jwt-data]
  (dissoc jwt-data [(jwt-attribute-email)
                    (jwt-attribute-firstname)
                    (jwt-attribute-lastname)
                    :iat
                    :max_age]))

(def ^:private ^:const three-minutes-in-seconds 180)

(defn- login-jwt-user
  [jwt redirect]
  (let [jwt-data            (jwt/unsign jwt (sso-settings/jwt-shared-secret)
                                        {:max-age three-minutes-in-seconds})
        login-attrs         (jwt-data->login-attributes jwt-data)
        email               (get jwt-data (jwt-attribute-email))
        first-name          (get jwt-data (jwt-attribute-firstname) "Unknown")
        last-name           (get jwt-data (jwt-attribute-lastname) "Unknown")
        {session-token :id} (jwt-auth-fetch-or-create-user! first-name last-name email login-attrs)]
    (resp/set-cookie (resp/redirect redirect)
                     "metabase.SESSION_ID" session-token
                     {:path "/"})))

(defn- check-jwt-enabled []
  (api/check (sso-settings/jwt-configured?)
    [400 (tru "JWT SSO has not been enabled and/or configured")]))

(defmethod api-saml/sso-get :jwt
  [req]
  (check-jwt-enabled)
  (if-let [jwt (get-in req [:params :jwt])]
    (login-jwt-user jwt (get-in req [:params :return_to]))
    (resp/redirect (str (sso-settings/jwt-identity-provider-uri)
                        (when-let [redirect (get-in req [:params :redirect])]
                          (str "?return_to=" redirect))))))

(defmethod api-saml/sso-post :jwt
  [req])
