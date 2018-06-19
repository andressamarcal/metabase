(ns metabase.mt.api.routes
  "Multi-tenant API routes."
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [context defroutes routes]]
            [metabase.middleware :as middleware]
            [metabase.mt.api
             [gtap :as gtap]
             [saml :as saml]
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
    (context "/user" [] (+auth user/routes)))))

(defroutes ^{:doc "Ring routes for auth (SAML) API endpoints."} auth-routes
  (context
   "/auth"
   []
   (routes
    (context "/sso" [] saml/routes))))

(defn install-mt-routes!
  "Swap out `metabase.api.routes/routes` with a new version that includes the multi-tenant routes. Take care to only
  call this once, or your life may be filled with nastiness!"
  []
  (log/info "Installing multi-tenant API and auth routes...")
  (require 'metabase.api.routes)
  (intern 'metabase.api.routes 'routes (routes mt-routes @(resolve 'metabase.api.routes/routes)))
  (require 'metabase.routes)
  (intern 'metabase.routes 'routes (routes auth-routes @(resolve 'metabase.routes/routes))))
