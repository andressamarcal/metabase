(ns metabase.enterprise.models.group-table-access-policy
  "Model definition for Group Table Access Policy, aka GTAP. A GTAP is useed to control access to a certain Table for a
  certain PermissionsGroup. Whenever a member of that group attempts to query the Table in question, a Saved Question
  specified by the GTAP is instead used as the source of the query."
  (:require [toucan.models :as models]
            [metabase.models.interface :as i]
            [metabase.util :as u]))

(models/defmodel GroupTableAccessPolicy :group_table_access_policy)

(u/strict-extend (class GroupTableAccessPolicy)
  models/IModel
  (merge
   models/IModelDefaults
   {:types (constantly {:attribute_remappings :json})})

  ;; only admins can work with GTAPs
  i/IObjectPermissions
  (merge
   i/IObjectPermissionsDefaults
   {:can-read?  i/superuser?
    :can-write? i/superuser?}))
