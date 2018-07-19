(ns metabase.mt.integrations.jwt
  "Implementation of the JWT backend for sso"
  (:require [buddy.sign.jwt :as jwt]
            [metabase.api
             [common :as api]
             [session :as session]]
            [metabase.mt.api.sso :as sso]
            [metabase.mt.integrations
             [sso-settings :as sso-settings]
             [sso-utils :as sso-utils]]
            [puppetlabs.i18n.core :refer [tru]]
            [ring.util.response :as resp]))

(defn jwt-auth-fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]

  (when-not (sso-settings/jwt-configured?)
    (throw (IllegalArgumentException. "Can't create new JWT user when JWT is not configured")))

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

;; JWTs use seconds since Epoch, not milliseconds since Epoch for the `iat` and `max_age` time. 3 minutes is the time
;; used by Zendesk for their JWT SSO, so it seemed like a good place for us to start
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
