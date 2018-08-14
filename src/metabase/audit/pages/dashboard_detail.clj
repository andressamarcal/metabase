(ns metabase.audit.pages.dashboard-detail
  (:require [metabase.audit.pages.common :as audit-common]
            [metabase.audit.pages.common.card-and-dashboard-detail :as card-and-dash-detail]
            [metabase.models
             [dashboard :refer [Dashboard]]
             [revision :as revision]]
            [metabase.util
             [honeysql-extensions :as hx]
             [schema :as su]]
            [schema.core :as s]
            [toucan.db :as db]))

(s/defn ^:internal-query-fn views-by-time
  "Get views of a Dashboard broken out by a time `unit`, e.g. `day` or `day-of-week`."
  [dashboard-id :- su/IntGreaterThanZero, unit :- audit-common/DateTimeUnitStr]
  (card-and-dash-detail/views-by-time "dashboard" dashboard-id unit))

(s/defn ^:internal-query-fn revision-history
  [dashboard-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/revision-history Dashboard dashboard-id))

(s/defn ^:internal-query-fn audit-log
  [dashboard-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/audit-log "dashboard" dashboard-id))
