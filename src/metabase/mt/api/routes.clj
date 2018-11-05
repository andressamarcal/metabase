(ns metabase.mt.api.routes
  "Multi-tenant API routes."
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [context defroutes routes]]
            [metabase.middleware :as middleware]
            [metabase.mt.api
             [gtap :as gtap]
             [sso :as sso]
             [table :as table]
             [user :as user]]))

;; this is copied from `metabase.api.routes` because if we require that above we will destroy startup times for `lein
;; ring server`
(def ^:private +auth
  "Wrap ROUTES so they may only be accessed with proper authentiaction credentials."
  middleware/enforce-authentication)

(defroutes ^{:doc "Ring routes for mt API endpoints."} mt-routes
  (context
   "/mt"
   []
   (routes
    (context "/gtap" [] (+auth gtap/routes))
    (context "/user" [] (+auth user/routes))))
  (context "/table" [] (+auth table/routes)))

(defroutes ^{:doc "Ring routes for auth (SAML) API endpoints."} auth-routes
  (context
   "/auth"
   []
   (routes
    (context "/sso" [] sso/routes))))
