(ns metabase.mt.integrations.jwt
  "Implementation of the JWT backend for sso"
  (:require [buddy.sign.jwt :as jwt]
            [metabase.api
             [common :as api]
             [session :as session]]
            [metabase.integrations.common :as integrations.common]
            [metabase.mt.api.sso :as sso]
            [metabase.mt.integrations
             [sso-settings :as sso-settings]
             [sso-utils :as sso-utils]]
            [metabase.util.i18n :refer [tru]]
            [ring.util.response :as resp])
  (:import java.net.URLEncoder))

(defn fetch-or-create-user!
  "Returns a session map for the given `email`. Will create the user if needed."
  [first-name last-name email user-attributes]
  (when-not (sso-settings/jwt-configured?)
    (throw (IllegalArgumentException. (str (tru "Can't create new JWT user when JWT is not configured")))))
  (or (sso-utils/fetch-and-update-login-attributes! email user-attributes)
      (sso-utils/create-new-sso-user! {:first_name       first-name
                                       :last_name        last-name
                                       :email            email
                                       :sso_source       "jwt"
                                       :login_attributes user-attributes})))

(def ^:private ^{:arglists '([])} jwt-attribute-email     (comp keyword sso-settings/jwt-attribute-email))
(def ^:private ^{:arglists '([])} jwt-attribute-firstname (comp keyword sso-settings/jwt-attribute-firstname))
(def ^:private ^{:arglists '([])} jwt-attribute-lastname  (comp keyword sso-settings/jwt-attribute-lastname))
(def ^:private ^{:arglists '([])} jwt-attribute-groups    (comp keyword sso-settings/jwt-attribute-groups))

(defn- jwt-data->login-attributes [jwt-data]
  (dissoc jwt-data
          (jwt-attribute-email)
          (jwt-attribute-firstname)
          (jwt-attribute-lastname)
          :iat
          :max_age))

;; JWTs use seconds since Epoch, not milliseconds since Epoch for the `iat` and `max_age` time. 3 minutes is the time
;; used by Zendesk for their JWT SSO, so it seemed like a good place for us to start
(def ^:private ^:const three-minutes-in-seconds 180)

(defn- group-names->ids [group-names]
  (set (mapcat (sso-settings/jwt-group-mappings)
               (map keyword group-names))))

(defn- sync-groups! [user jwt-data]
  (when (sso-settings/jwt-group-sync)
    (when-let [groups-attribute (jwt-attribute-groups)]
      (when-let [group-names (get jwt-data (jwt-attribute-groups))]
        (integrations.common/sync-group-memberships! user (group-names->ids group-names))))))

(defn- login-jwt-user
  [jwt redirect]
  (let [jwt-data      (jwt/unsign jwt (sso-settings/jwt-shared-secret)
                                  {:max-age three-minutes-in-seconds})
        login-attrs   (jwt-data->login-attributes jwt-data)
        email         (get jwt-data (jwt-attribute-email))
        first-name    (get jwt-data (jwt-attribute-firstname) "Unknown")
        last-name     (get jwt-data (jwt-attribute-lastname) "Unknown")
        user          (fetch-or-create-user! first-name last-name email login-attrs)
        session-token (session/create-session! user)]
    (sync-groups! user jwt-data)
    (resp/set-cookie (resp/redirect (or redirect (URLEncoder/encode "/")))
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
