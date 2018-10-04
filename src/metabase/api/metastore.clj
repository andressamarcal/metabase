(ns metabase.api.metastore
  (:require [compojure.core :refer [GET]]
            [metabase.api.common :as api]
            [metabase.public-settings.metastore :as metastore]))

(api/defendpoint GET ["/token/:token/status" :token #"[0-9a-f]{64}"]
  "Fetch info about MetaStore premium features token include whether it is `valid`, a `trial` token, its `features`, and
  when it is `valid_thru`."
  [token]
  (metastore/fetch-token-status token))

(api/define-routes api/+check-superuser)
