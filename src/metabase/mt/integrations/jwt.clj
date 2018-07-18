(ns metabase.mt.integrations.jwt
  (:require [buddy.sign.jwt :as jwt]
            [metabase.api.common :as api]
            [metabase.mt.integrations.sso-settings :as sso-settings]
            [metabase.mt.api.sso :as sso]
            [ring.util.response :as resp]
            [metabase.util :as u]
            [toucan.db :as db]
            [metabase.models.user :refer [User]]
            [metabase.email.messages :as email]
            [metabase.api.session :as session]
            [puppetlabs.i18n.core :refer [tru]]
            [metabase.mt.integrations.sso-utils :as sso-utils]))

(defn jwt-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]

  (when-not (sso-settings/jwt-configured?)
    (throw (IllegalArgumentException. "Can't create new SAML user when SAML is not configured")))

  (when-let [user (or (sso-utils/fetch-and-update-login-attributes! email user-attributes)
                      (sso-utils/create-new-sso-user! {:first_name first-name
                                                       :last_name  last-name
                                                       :email      email
                                                       :sso_source "jwt"}))]
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

(defmethod sso/sso-get :jwt
  [req]
  (check-jwt-enabled)
  (if-let [jwt (get-in req [:params :jwt])]
    (login-jwt-user jwt (get-in req [:params :return_to]))
    (resp/redirect (str (sso-settings/jwt-identity-provider-uri)
                        (when-let [redirect (get-in req [:params :redirect])]
                          (str "?return_to=" redirect))))))

(defmethod sso/sso-post :jwt
  [req]
  (throw (ex-info "POST not valid for JWT SSO requests" {:status-code 400})))
