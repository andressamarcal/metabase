(ns metabase.mt.api.table
  (:require [compojure.core :refer [GET]]
            [metabase.api
             [common :as api]
             [table :as table-api]]
            [metabase.models
             [card :refer [Card]]
             [permissions :as perms]
             [permissions-group-membership :refer [PermissionsGroupMembership]]
             [table :as table :refer [Table]]]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.query-processor.middleware.expand :as expand]
            [metabase.query-processor.util :as qputil]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan
             [db :as db]
             [models :as models]]))

(s/defn ^:private find-gtap-question :- (s/maybe (type Card))
  "Find the associated GTAP question (if there is one) for the given `table-or-table-id` and
  `user-or-user-id`. Returns nil if no question was found."
  [table-or-table-id user-or-user-id]
  (some->> (db/query {:select [:c.id :c.dataset_query]
                      :from [[GroupTableAccessPolicy :gtap]]
                      :join [[PermissionsGroupMembership :pgm] [:= :gtap.group_id :pgm.group_id ]
                             [Card :c] [:= :c.id :gtap.card_id]]
                      :where [:and
                              [:= :gtap.table_id (u/get-id table-or-table-id)]
                              [:= :pgm.user_id (u/get-id user-or-user-id)]]})
           first
           (models/do-post-select Card)))

(s/defn ^:private get-field-id :- s/Int
  "Fields in MBQL `:fields` clauses can be integers (MBQL '95) or can be `[:field-id <int>]` which then get translated
  into a field placeholder. This function handles that difference and always returns the integer field ID"
  [field-ph-or-field-id]
  (if (integer? field-ph-or-field-id)
    field-ph-or-field-id
    (:field-id field-ph-or-field-id)))

(s/defn ^:private only-segmented-perms? :- s/Bool
  "Returns true if the user has only segemented and not full table permissions. If the user has full table permissions
  we wouldn't want to apply this segment filtering."
  [table :- (type Table)]
  (and
   (not (perms/set-has-full-permissions? @api/*current-user-permissions-set*
          (perms/table-query-path table)))
   (perms/set-has-full-permissions? @api/*current-user-permissions-set*
     (perms/table-segmented-query-path table))))

(s/defn ^:private query->fields-ids :- (s/maybe [s/Int])
  [{:keys [dataset_query] :as card}]
  (some->> (qputil/get-in-normalized dataset_query [:query :fields])
           (map expand/expand-ql-sexpr)
           (map get-field-id)))

(defn- maybe-filter-fields [table query-metadata-response]
  ;; If we have segmented permissions and the associated GTAP limits the fields returned, we need make sure the
  ;; query_metadata endpoint also excludes any fields the GTAP query would exclude
  (if-let [gtap-field-ids (and (only-segmented-perms? table)
                               (seq (query->fields-ids (find-gtap-question table api/*current-user-id*))))]
    (update query-metadata-response :fields #(filter (comp (set gtap-field-ids) u/get-id) %))
    query-metadata-response))

(api/defendpoint GET "/:id/query_metadata"
  "This endpoint essentially acts as a wrapper for the OSS version of this route. When a user has segmented
  permissions that only gives them access to a subset of columns for a given table, those inaccessable columns should
  also be excluded from what is show in the query builder. When the user has full permissions (or no permissions) this
  route doesn't add/change anything from the OSS version. See the docs on the OSS version of the endpoint for more
  information."
  [id include_sensitive_fields]
  {include_sensitive_fields (s/maybe su/BooleanString)}
  ;; Permissions checking for table is done in `fetch-query-metadata`
  (let [table (Table id)]
    (maybe-filter-fields table (table-api/fetch-query-metadata table include_sensitive_fields))))

(api/define-routes)