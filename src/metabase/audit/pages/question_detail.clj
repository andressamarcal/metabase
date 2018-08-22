(ns metabase.audit.pages.question-detail
  "Detail page for a single Card (Question)."
  (:require [metabase.audit.pages.common :as common]
            [metabase.audit.pages.common.card-and-dashboard-detail :as card-and-dash-detail]
            [metabase.models.card :refer [Card]]
            [metabase.util.schema :as su]
            [schema.core :as s]))

(s/defn ^:internal-query-fn views-by-time
  "Get views of a Card broken out by a time `unit`, e.g. `day` or `day-of-week`."
  [card-id :- su/IntGreaterThanZero, datetime-unit :- common/DateTimeUnitStr]
  (card-and-dash-detail/views-by-time "card" card-id datetime-unit))


(s/defn ^:internal-query-fn revision-history
  [card-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/revision-history Card card-id))

(s/defn ^:internal-query-fn audit-log
  [card-id :- su/IntGreaterThanZero]
  (card-and-dash-detail/audit-log "card" card-id))
