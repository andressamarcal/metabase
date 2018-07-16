(ns metabase.mt.api.saml
  (:require [clojure.string :as str]
            [compojure.core :refer [GET POST]]
            [medley.core :as m]
            [metabase.mt.integrations.sso-settings :as sso-settings]
            [metabase.api.common :as api]
            [metabase.public-settings :as public-settings]
            [puppetlabs.i18n.core :refer [tru]]
            [ring.util.response :as resp]))

(defn- sso-backend [_]
  (cond
    (sso-settings/saml-configured?)
    :saml
    (sso-settings/jwt-enabled)
    :jwt
    :else
    nil))

(defmulti sso-get
  "Multi-method for supporting the first part of an SSO signin request. An implementation of this method will usually
  result in a redirect to an SSO backend"
  sso-backend)

(defmulti sso-post
  "Multi-method for supporting a POST-back from an SSO signin request. An implementation of this method will need to
  validate the POST from the SSO backend and successfully log the user into Metabase."
  sso-backend)

(defmethod sso-get :default
  [_]
  (throw (ex-info (tru "SSO has not been enabled and/or configured")
           {:status-code 400})))

(defmethod sso-post :default
  [_]
  (throw (ex-info (tru "SSO has not been enabled and/or configured")
           {:status-code 400})))

(api/defendpoint GET "/"
  "SSO entry-point for an SSO user that has not logged in yet"
  {:as req}
  (sso-get req))

(api/defendpoint POST "/"
  "Route the SSO backends call with successful login details"
  {:as req}
  (sso-post req))

(api/define-routes)
