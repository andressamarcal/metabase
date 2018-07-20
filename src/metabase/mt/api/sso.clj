(ns metabase.mt.api.sso
  "Implements the SSO routes needed for SAML and JWT. This namespace primarily provides hooks for those two backends
  so we can have a uniform interface both via the API and code"
  (:require [compojure.core :refer [GET POST]]
            [metabase.api.common :as api]
            [metabase.mt.integrations.sso-settings :as sso-settings]
            [puppetlabs.i18n.core :refer [tru]]))

(defn- sso-backend
  "Function that powers the defmulti in figuring out which SSO backend to use. It might be that we need to have more
  complex logic around this, but now it's just a simple priority. If SAML is configured use that otherwise JWT"
  [_]
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
