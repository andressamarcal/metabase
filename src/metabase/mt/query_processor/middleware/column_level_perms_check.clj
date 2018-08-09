(ns metabase.mt.query-processor.middleware.column-level-perms-check
  (:require [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [metabase.api.common :refer [*current-user-id*]]
            [metabase.mt.query-processor.middleware.util :as mt-util]
            [metabase.query-processor.middleware.add-implicit-clauses :as add-implicit]
            [puppetlabs.i18n.core :refer [trs tru]])
  (:import metabase.query_processor.interface.FieldPlaceholder))

(defn- collect-all-fields [outer-query]
  (let [ids (transient #{})]
    (walk/postwalk (fn [form]
                     (when (instance? FieldPlaceholder form)
                       (conj! ids (:field-id form))))
                   (update outer-query [:query] dissoc :source-query))
    (persistent! ids)))

(defn- maybe-apply-column-level-perms-check [qp]
  (fn [query]
    (let [restricted-field-ids (and (or (:gtap-perms query)
                                        (get-in query [:query :gtap-perms]))
                                    (set (map :field-id (get-in query [:query :source-query :fields]))))]
      (when (seq restricted-field-ids)
        (let [fields-in-query (collect-all-fields query)]
          (when-not (every? restricted-field-ids fields-in-query)
            (log/warn (trs "User ''{0}'' attempted to access an inaccessible field. Accessible fields {1}, fields in query {2}"
                           *current-user-id* (pr-str restricted-field-ids) (pr-str fields-in-query)))
            (throw (ex-info (tru "User not able to query field") {:status 403})))))
      (qp query))))

(defn update-qp-pipeline
  "Update the query pipeline atom to include the column level perms check middleware. Intended to be called on startup."
  []
  (mt-util/update-qp-pipeline #'add-implicit/add-implicit-clauses #'maybe-apply-column-level-perms-check))
