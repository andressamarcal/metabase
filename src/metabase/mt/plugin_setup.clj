(ns metabase.mt.plugin-setup
  "Logic for doing var swaps and other things needed to enable Metabase® Multi-Tenant Edition™ capabilitities."
  (:require [metabase.mt.api.routes :as mt-api-routes]
            [metabase.mt.query-processor.middleware.row-level-restrictions :as rlr]))

(defn -init-plugin!
  "Do all of the hackery needed to turn this instance into a Metabase® Multi-Tenant Edition™ instance."
  []
  (mt-api-routes/install-mt-routes!)
  (rlr/update-qp-pipeline-for-mt))
