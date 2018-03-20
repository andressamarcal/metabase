(ns metabase.mt.api.gtap
  "`/api/mt/gtap` endpoints, for CRUD operations and the like on GTAPs (Group Table Access Policies)."
  (:require [compojure.core :refer [DELETE GET POST PUT]]
            [metabase.api.common :as api]
            [metabase.mt.models.group-table-access-policy :refer [GroupTableAccessPolicy]]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan.db :as db]))

(api/defendpoint GET "/"
  "Fetch a list of all the GTAPs currently in use."
  []
  ;; TODO - do we need to hydrate anything here?
  (db/select GroupTableAccessPolicy))

;; TODO - not sure what other endpoints we might need, e.g. for fetching the list above but for a given group or Table

(def ^:private AttributeRemappings
  (su/with-api-error-message (s/maybe {su/NonBlankString su/NonBlankString})
    "value must be a valid attribute remappings map (attribute name -> remapped name)"))

(api/defendpoint POST "/"
  "Create a new GTAP."
  [:as {{:keys [table_id card_id group_id attribute_remappings]} :body}]
  {table_id             su/IntGreaterThanZero
   card_id              su/IntGreaterThanZero
   group_id             su/IntGreaterThanZero
   attribute_remappings AttributeRemappings}
  (db/insert! GroupTableAccessPolicy
    {:table_id             table_id
     :card_id              card_id
     :group_id             group_id
     :attribute_remappings attribute_remappings}))

(api/defendpoint PUT "/:id"
  "Update a GTAP entry. The only things you're allowed to update for a GTAP are the Card being used (`card_id`) or the
  paramter mappings; changing `table_id` or `group_id` would effectively be deleting this entry and creating a new
  one. If that's what you want to do, do so explicity with appropriate calls to the `DELETE` and `POST` endpoints."
  [id :as {{:keys [card_id attribute_remappings], :as body} :body}]
  {card_id              (s/maybe su/IntGreaterThanZero)
   attribute_remappings AttributeRemappings}
  (api/check-404 GroupTableAccessPolicy id)
  ;; only update `card_id` and/or `attribute_remappings` if non-nil values were passed in. That way this endpoint can
  ;; be used to update only one value or the other. Ignore everything else.
  (db/update! GroupTableAccessPolicy id
    (u/select-non-nil-keys body [:card_id :attribute_remappings])))

(api/defendpoint DELETE "/:id"
  "Delete a GTAP entry."
  [id]
  (api/check-404 GroupTableAccessPolicy id)
  (db/delete! GroupTableAccessPolicy :id id)
  api/generic-204-no-content)


;; All endpoints in this namespace require superuser perms to view
;;
;; TODO - does it make sense to have this middleware
;; here? Or should we just wrap `routes` in the `metabase.mt.api.routes/routes` table like we do for everything else?
;;
;; TODO - defining the `check-superuser` check *here* means the API documentation function won't pick up on the "this
;; requires a superuser" stuff since it parses the `defendpoint` body to look for a call to `check-superuser`. I
;; suppose this doesn't matter (much) since this is an enterprise endpoint and won't go in the dox anyway.
(api/define-routes api/+check-superuser)
