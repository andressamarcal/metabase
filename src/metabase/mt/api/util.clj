(ns metabase.mt.api.util
  "Enterprise specific API utility functions"
  (:require [metabase.api.common :refer [*current-user-permissions-set*]]
            [metabase.models.permissions :as perms]
            [puppetlabs.i18n.core :refer [tru]]))

(defn segmented-user?
  "Returns true if the currently logged in user has segmented permissions"
  []
  (if-let [current-user-perms @*current-user-permissions-set*]
    (boolean (some #(re-matches perms/segmented-perm-regex %) current-user-perms))
    ;; If the current permissions are nil, then we would return false which could give a potentially segmented user
    ;; access they shouldn't have. If we don't have permissions, we can't determine whether they are segmented, so
    ;; throw.
    (throw (ex-info (tru "No permissions found for current user")
             {:status-code 403}))))
