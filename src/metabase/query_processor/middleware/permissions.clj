(ns metabase.query-processor.middleware.permissions
  "Middleware for checking that the current user has permissions to run the current query.
   TODO - this could probably be simplified a bit to reuse some of the same logic that determines permissions for
  Cards in `metabase.models.card`. "
  (:require [clojure.tools.logging :as log]
            [metabase.api.common :refer [*current-user-id* *current-user-permissions-set*]]
            [metabase.models.permissions :as perms]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [metabase.query-processor.util :as qputil]
            [toucan
             [db :as db]
             [hydrate :refer [hydrate]]]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                   Helper Fns                                                   |
;;; +----------------------------------------------------------------------------------------------------------------+


(defn- log-permissions-debug-message {:style/indent 2} [color format-str & format-args]
  (let [appropriate-lock-emoji (if (= color 'yellow)
                                 "🔒"   ; lock (closed)
                                 "🔓")] ; lock (open
    (log/debug (u/format-color color (apply format (format "Permissions Check %s : %s" appropriate-lock-emoji format-str)
                                            format-args)))))

(defn- log-permissions-success-message {:style/indent 1} [format-string & format-args]
  (log-permissions-debug-message 'green (str "Yes ✅  " (apply format format-string format-args))))

(defn- log-permissions-error []
  (log/warn (u/format-color 'red "Permissions Check 🔐 : No 🚫"))) ; lock (closed)

;; TODO - what status code / error message should we use when someone doesn't have permissions?
(defn- throw-permissions-exception {:style/indent 1} [format-str & format-args]
  (log-permissions-error)
  (throw (Exception. ^String (apply format format-str format-args))))

(defn- throw-if-user-doesnt-have-permissions-for-path
  "Check whether current user has permissions for OBJECT-PATH, and throw an exception if not.
   Log messages related to the permissions checks as well."
  [object-path]
  (log-permissions-debug-message 'yellow "Does user have permissions for %s?" object-path)
  (when-not (perms/set-has-full-permissions? @*current-user-permissions-set* object-path)
    (throw-permissions-exception "You do not have read permissions for %s." object-path))
  ;; permissions check out, now log which perms we've been granted that allowed our escapades to proceed
  (log-permissions-success-message "because user has permissions for %s."
    (some (fn [permissions-path]
            (when (perms/is-permissions-for-object? permissions-path object-path)
              permissions-path))
          @*current-user-permissions-set*)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          Perms Check Implementations                                           |
;;; +----------------------------------------------------------------------------------------------------------------+

;; Depending on the query, one of several different permissions checks will apply to it; depending on what we're
;; doing, we either want to actually run those permissions checks, or just use the knowledge of which permissions
;; check will occur to make some decisions, for example whether or not we need to apply a GTAP for segmented
;; permissions.
;;
;; Since there are a few different use cases, each of the possible permssions checks is encapsulated in a record type
;; that records the check that needs to take place. To figure out which check needs to take place for a given query,
;; use `query->perms-check`:
;;
;;     (query->perms-check query) ; -> #CollectionPermsCheck{:collection-id 10}
;;
;; This function will return an appropriate perms check object, or `nil` if no permissions checking needs to take place.
;;
;; To perform the permissions check itself, simply invoke the object (each object implements `IFn`):
;;
;;     (when-let [perms-check (query->perms-check query)]
;;       (perms-check))
;;
;; These functions do nothing (other than logging) if the permissions check is successful; if the current user does
;; not pass the check, they will throw an Exception. Since these functions check permissions for the current user,
;; `*current-user-id*` and `*current-user-permissions-set*` should both be bound before invoking the permissions
;; check.
;;
;; Note that these objects representing the checks to take place do not neccesarily need to be invoked; in other cases
;; (such as segmented permissions) they can instead be introspected to see which source-table the query references and
;; check permissions appropriately.

(s/defrecord CollectionPermsCheck [collection-id :- su/IntGreaterThanZero]
  clojure.lang.IFn
  (invoke [_]
    (throw-if-user-doesnt-have-permissions-for-path (perms/collection-read-path collection-id))))

(s/defrecord NewNativeQueryPermsCheck [database-id :- su/IntGreaterThanZero]
  clojure.lang.IFn
  (invoke [_]
    (throw-if-user-doesnt-have-permissions-for-path (perms/native-readwrite-path database-id))))

(s/defrecord ExistingNativeQueryPermsCheck [database-id :- su/IntGreaterThanZero]
  clojure.lang.IFn
  (invoke [_]
    (throw-if-user-doesnt-have-permissions-for-path (perms/native-read-path database-id))))

(s/defrecord TablesPermsCheck [source-table-id :- su/IntGreaterThanZero
                               join-table-ids  :- (s/maybe #{su/IntGreaterThanZero})]
  clojure.lang.IFn
  (invoke [_]
    ;; You are allowed to run a query against a Table *if* you have partial or full query permissions. e.g. if you
    ;; have `query/segmented` permissions we will still let you thru the door at this point. The MT middleware will
    ;; handle that stuff appropriately.
    (doseq [table (db/select ['Table :id :db_id :schema] :id [:in (cons source-table-id join-table-ids)])]
      (when-not (perms/set-has-partial-permissions? @*current-user-permissions-set* (perms/table-query-path table))
        (throw-permissions-exception "You do not have permissions to run queries referencing table %d."
          (u/get-id table))))))



;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                    Logic to Decide Which Perms Check to Do                                     |
;;; +----------------------------------------------------------------------------------------------------------------+

(def ^:private QueryPermsCheck
  (s/cond-pre CollectionPermsCheck
              NewNativeQueryPermsCheck
              ExistingNativeQueryPermsCheck
              TablesPermsCheck))

(s/defn ^:private table->id :- su/IntGreaterThanZero
  "Return the ID of a Table, regardless of the possible format it's currently in.

  Depending on which stage of query expansion we're at, keys like `:source-table` might either still be a raw Table ID
  or may have already been 'resolved' and replaced with the full Table object. Additional, there are some differences
  between `:join-tables` and `:source-table` using `:id` vs `:table-id`. These inconsistencies are annoying, but
  luckily this function exists to handle any possible case and always return the ID."
  [table]
  (when-not table
    (throw (Exception. "Error: table is nil")))
  (or (when (integer? table) table)
      (:id table)
      (:table-id table)))

(s/defn query->perms-check :- (s/maybe QueryPermsCheck)
  "Given a `query`, return an object representing the permissions check that needs to take place. Object will be one of
  the record types above, or `nil` if no permissions check needs to happen."
  [{query-type :type, :keys [database query source-table-is-gtap?], {:keys [card-id]} :info, :as outer-query}]
  {:pre [(map? outer-query)]}
  (let [native?      (= (keyword query-type) :native)
        source-query (qputil/get-normalized query :source-query)

        {collection-id :collection_id, public-uuid :public_uuid, in-public-dash? :in_public_dashboard}
        (-> (db/select-one ['Card :id :collection_id :public_uuid] :id card-id)
            (hydrate :in_public_dashboard))]
    (cond
      ;; if the card itself is public, or if its in a Public dashboard, you are always allowed to run its query
      (or public-uuid in-public-dash?)
      nil

      ;; if the card is in a COLLECTION, then see if the current user has permissions for that collection
      collection-id
      (strict-map->CollectionPermsCheck {:collection-id collection-id})

      ;; Otherwise if this is a NESTED query then we should check permissions for the source query
      source-query
      (if (:native source-query)
        ;; for a native source query, we just need to have perms to run an existing native query
        (strict-map->ExistingNativeQueryPermsCheck {:database-id (u/get-id database)})
        ;; for an MBQL source query, recusively check whether we have permissions to run it
        (query->perms-check (assoc outer-query :query source-query)))

      ;; if we're dealing with a GTAP we don't want to check perms since it was actually put in place specifically
      ;; because of the permissions for the current user
      source-table-is-gtap?
      nil

      ;; for native queries that are *not* part of an existing card, check that we have native permissions for the DB
      (and native? (not card-id))
      (strict-map->NewNativeQueryPermsCheck {:database-id (u/get-id database)})

      ;; for native queries that *are* part of an existing card, just check if the have native read permissions
      native?
      (strict-map->ExistingNativeQueryPermsCheck {:database-id (u/get-id database)})

      ;; for MBQL queries (existing card or not), check that we can run against the source-table and each of the
      ;; join-tables, if any
      (not native?)
      (strict-map->TablesPermsCheck {:source-table-id (table->id (qputil/get-normalized query :source-table))
                                     :join-table-ids  (set (map table->id (qputil/get-normalized query :join-tables)))}))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                   Middleware                                                   |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- check-query-permissions* [query]
  (when *current-user-id*
     (when-let [perms-check (query->perms-check query)]
       (perms-check)))
  query)

(defn check-query-permissions
  "Middleware that check that the current user has permissions to run the current query. This only applies if
  `*current-user-id*` is bound. In other cases, like when running public Cards or sending pulses, permissions need to
  be checked separately before allowing the relevant objects to be create (e.g., when saving a new Pulse or
  'publishing' a Card)."
  [qp]
  (comp qp check-query-permissions*))
