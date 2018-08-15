(ns metabase.query-processor.middleware.internal-queries
  "Middleware that handles special `internal` type queries. `internal` queries are implementeed directly by Clojure
  functions, and do not neccesarily need to query a database to provide results; by default, they completely skip
  the rest of the normal QP pipeline. `internal` queries should look like the following:

    {:type :internal
     :fn   \"metabase.audit.pages.dashboards/table\"
     :args []} ; optional vector of args to pass to the fn above

  To run an `internal` query, you must have superuser permissions, and the function itself must be tagged as an
  `:internal-query-fn`. This middleware will automatically resolve the function as appropriate, loading its namespace
  if needed. The function should return a map with two keys, `:metadata` and `:results`:

    (defn ^:internal-query-fn table []
      {:metadata [[:title {:display_name \"Title\", :base_type :type/Text}]
                  [:count {:display_name \"Count\", :base_type :type/Integer}]]
       :results  [{:title \"Birds\", :count 2}
                  {:title \"Cans\", :count 2}]})

  *  `:metadata` is used to provide the normal `:columns` and `:cols` metadata that would come back with an MBQL or
     native query.
  *  `:results` should be a sequence of maps similar to the results of `jdbc/query`. They will be automatically
     converted to the format expected by the frontend (a `rows` key that is an array of arrays)  by this middleware."
  (:require [clojure
             [data :as data]
             [string :as str]]
            [metabase.api.common :as api]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [puppetlabs.i18n.core :refer [tru]]
            [schema.core :as s]))

(def ^:private ResultsMetadata
  "Schema for the expected format for `:metadata` returned by an internal query function."
  (su/non-empty
   [[(s/one su/KeywordOrString "field name")
     (s/one {:base_type su/FieldType, :display_name su/NonBlankString, s/Keyword s/Any}
            "field metadata")]]))

(defn- check-results-and-metadata-keys-match
  "Primarily for dev and debugging purposes. We can probably take this out when shipping the finished product."
  [results metadata]
  (let [results-keys  (set (keys (first results)))
        metadata-keys (set (map (comp keyword first) metadata))]
    (when (and (seq results-keys)
               (not= results-keys metadata-keys))
      (let [[only-in-results only-in-metadata] (data/diff results-keys metadata-keys)]
        (println "Warning: results-keys and metadata-keys differ.\n"
                 "results-keys:" results-keys "\n"
                 "metadata-keys:" metadata-keys "\n"
                 "in results, but not metadata:" only-in-results "\n"
                 "in metadata, but not results:" only-in-metadata)))))

(s/defn ^:private format-results [{:keys [results metadata]} :- {:results  [su/Map]
                                                                 :metadata ResultsMetadata}]
  (check-results-and-metadata-keys-match results metadata)
  {:status    :completed
   :row_count (count results)
   :data      {:columns (map first metadata)
               :cols    (for [[k v] metadata]
                          (assoc v :name (name k)))
               :rows    (for [row results]
                          (for [[k] metadata]
                            (get row (keyword k))))}})

(def InternalQuery
  "Schema for a valid `internal` type query."
  {:type                    (s/enum :internal "internal")
   :fn                      #"^([\w\d-]+\.)*[\w\d-]+/[\w\d-]+$" ; namespace-qualified symbol
   (s/optional-key :args)   [s/Any]
   s/Any                    s/Any})

(def ^:dynamic *additional-query-params* nil)

(s/defn ^:private do-internal-query
  [{qualified-fn-str :fn, args :args, :as query} :- InternalQuery]
  ;; Make sure current user is a superuser
  (api/check-superuser)
  ;; now resolve the query
  (let [[ns-str] (str/split qualified-fn-str #"/")
        _        (require (symbol ns-str))
        fn-varr  (or (resolve (symbol qualified-fn-str))
                     (throw
                      (Exception.
                       (str (tru "Unable to run internal query function: cannot resolve {0}"
                                 qualified-fn-str)))))]
    ;; Make sure this is actually allowed to be a internal query fn & has the results metadata we'll need
    (when-not (:internal-query-fn (meta fn-varr))
      (throw (Exception. (str (tru "Invalid internal query function: {0} is not marked as an ^:internal-query-fn"
                                   qualified-fn-str)))))
    ;; if this function is marked deprecated log a warning.
    ;; Primarily for dev/testing purposes. TODO - remove this once v1 [beta] is done or upgrade it to use log/ + i18n
    (when (:deprecated (meta fn-varr))
      (println (u/format-color 'red
                   (str "Warning: %s is marked deprecated. This is probably because it's not in the new designs. "
                        "This function will be removed in the [very] near future.")
                 qualified-fn-str)))
    ;; ok, run the query
    (format-results (binding [*additional-query-params* (dissoc query :fn :args)]
                      (apply @fn-varr args)))))


(defn handle-internal-queries
  "Middleware that handles `internal` type queries."
  [qp]
  (fn [{query-type :type, :as query}]
    (if (= :internal (keyword query-type))
      (do-internal-query query)
      (qp query))))
