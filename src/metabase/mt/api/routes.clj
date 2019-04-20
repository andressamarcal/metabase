(ns metabase.mt.api.routes
  "Multi-tenant API routes."
  (:require [compojure.core :refer [context defroutes routes]]
            [metabase.middleware.auth :as middleware.auth]
            [metabase.mt.api
             [gtap :as gtap]
             [sso :as sso]
             [table :as table]
             [user :as user]]))

;; this is copied from `metabase.api.routes` because if we require that above we will destroy startup times for `lein
;; ring server`
(def ^:private +auth
  "Wrap ROUTES so they may only be accessed with proper authentiaction credentials."
  middleware.auth/enforce-authentication)

(defroutes ^{:doc "Ring routes for mt API endpoints."} mt-api-routes
  (context
   "/mt"
   []
   (routes
    (context "/gtap" [] (+auth gtap/routes))
    (context "/user" [] (+auth user/routes))))
  (context "/table" [] (+auth table/routes)))

;; This needs to be installed in the `metabase.routes/routes` -- not `metabase.api.routes/routes` !!!
(defroutes ^{:doc "Ring routes for auth (SAML) API endpoints."} auth-routes
  (context
   "/auth"
   []
   (routes
    (context "/sso" [] sso/routes))))
