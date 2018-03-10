(ns metabase.mt.api.routes
  "Multi-tenant API routes."
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes context routes]]
            [metabase.api.routes :as api-routes]
            [metabase.mt.api.gtap :as gtap]))

(defroutes ^{:doc "Ring routes for mt API endpoints."} mt-routes
  (context
   "/mt"
   []
   (routes
    (context "/gtap" [] (api-routes/+auth gtap/routes)))))

(def ^:private combined-routes
  "A combined Compojure routes object that combines the `mt-routes` above with the normal API routes.
   (This is just the multi-tenant routes list above with the normal routes added afterwards.)"
  (routes
   mt-routes
   api-routes/routes))


(defn install-mt-routes!
  "Swap out `metabase.api.routes/routes` with a new version that includes the multi-tenant routes. Take care to only
  call this once, or your life may be filled with nastiness!"
  []
  (log/info "Installing multi-tenant API routes...")
  (intern 'metabase.api.routes 'routes combined-routes))
