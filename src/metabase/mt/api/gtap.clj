(ns metabase.mt.api.gtap
  "`/api/mt/gtap` endpoints, for CRUD operations and the like on GTAPs (Group Table Access Policies)."
  (:require [compojure.core :refer [GET]]
            [metabase.api.common :as api]))

(api/defendpoint GET "/"
  "Test endpoint to make sure the endpoint 'injection' stuff is actually working."
  []
  {:does-it-work? true})


(api/define-routes)
