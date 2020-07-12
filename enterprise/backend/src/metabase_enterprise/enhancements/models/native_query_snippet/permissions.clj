(ns metabase-enterprise.enhancements.models.native-query-snippet.permissions
  "EE implementation of NativeQuerySnippet permissions."
  (:require [metabase.models
             [interface :as i]
             [permissions :as perms]]
            [metabase.models.native-query-snippet.permissions :as snippet.perms]
            [metabase.public-settings.metastore :as settings.metastore]
            [metabase.util.schema :as su]
            [pretty.core :refer [PrettyPrintable]]
            [schema.core :as s]
            [toucan.db :as db]))

(s/defn ^:private has-parent-collection-perms?
  [snippet       :- {:collection_id (s/maybe su/IntGreaterThanZero), s/Keyword s/Any}
   read-or-write :- (s/enum :read :write)]
  (i/current-user-has-full-permissions? (perms/perms-objects-set-for-parent-collection snippet read-or-write)))

(def ee-impl
  "EE implementation of NativeQuerySnippet permissions. Uses Collection permissions instead allowing anyone to view or
  edit all Snippets."
  (reify
    PrettyPrintable
    (pretty [_]
      `ee-impl)

    snippet.perms/PermissionsImpl
    (can-read?* [_ snippet]
      (if (settings.metastore/enable-enhancements?)
        (has-parent-collection-perms? snippet :read)
        (snippet.perms/can-read?* snippet.perms/default-impl snippet)))

    (can-read?* [_ model id]
      (if (settings.metastore/enable-enhancements?)
        (has-parent-collection-perms? (db/select-one [model :collection_id] :id id) :read)
        (snippet.perms/can-read?* snippet.perms/default-impl model id)))

    (can-write?* [_ snippet]
      (if (settings.metastore/enable-enhancements?)
        (has-parent-collection-perms? snippet :write)
        (snippet.perms/can-write?* snippet.perms/default-impl snippet)))

    (can-write?* [_ model id]
      (if (settings.metastore/enable-enhancements?)
        (has-parent-collection-perms? (db/select-one [model :collection_id] :id id) :write)
        (snippet.perms/can-write?* snippet.perms/default-impl model id)))

    (can-create?* [_ model m]
      (if (settings.metastore/enable-enhancements?)
        (has-parent-collection-perms? m :write)
        (snippet.perms/can-create?* snippet.perms/default-impl model m)))

    (can-update?* [_ snippet changes]
      (if (settings.metastore/enable-enhancements?)
        (and (has-parent-collection-perms? snippet :write)
             (or (not (contains? changes :collection_id))
                 (has-parent-collection-perms? changes :write)))
        (snippet.perms/can-update?* snippet.perms/default-impl snippet changes)))))

(snippet.perms/set-impl! ee-impl)
