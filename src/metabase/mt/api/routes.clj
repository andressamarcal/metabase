(ns metabase.mt.api.routes
  "Multi-tenant API routes."
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes context routes]]
            [metabase.mt.api
             [gtap :as gtap]
             [user :as user]]
            [metabase.middleware :as middleware]))

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
    ;; TODO - HOW SHOULD WE SECURE THIS!!!!!!!!!!!
    (context "/user" [] user/routes))))

(defn install-mt-routes!
  "Swap out `metabase.api.routes/routes` with a new version that includes the multi-tenant routes. Take care to only
  call this once, or your life may be filled with nastiness!"
  []
  (log/info "Installing multi-tenant API routes...")
  (require 'metabase.api.routes)
  (intern 'metabase.api.routes 'routes (routes mt-routes @(resolve 'metabase.api.routes/routes))))
