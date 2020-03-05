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
            [metabase.public-settings.metastore :as metastore]
            [metabase.query-processor.context :as context]
            [metabase.util
             [i18n :refer [tru]]
             [schema :as su]]
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
        (throw
         (Exception.
          (str "results-keys and metadata-keys differ.\n"
               "results-keys:" results-keys "\n"
               "metadata-keys:" metadata-keys "\n"
               "in results, but not metadata:" only-in-results "\n"
               "in metadata, but not results:" only-in-metadata)))))))

(s/defn ^:private format-results [{:keys [results metadata]} :- {:results  [su/Map]
                                                                 :metadata ResultsMetadata}]
  (check-results-and-metadata-keys-match results metadata)
  {:cols (for [[k v] metadata]
           (assoc v :name (name k)))
   :rows (for [row results]
           (for [[k] metadata]
             (get row (keyword k))))})

(def InternalQuery
  "Schema for a valid `internal` type query."
  {:type                    (s/enum :internal "internal")
   :fn                      #"^([\w\d-]+\.)*[\w\d-]+/[\w\d-]+$" ; namespace-qualified symbol
   (s/optional-key :args)   [s/Any]
   s/Any                    s/Any})

(def ^:dynamic *additional-query-params*
  "Additional `internal` query params beyond `type`, `fn`, and `args`. These are bound to this dynamic var which is a
  chance to do something clever outside of the normal function args. For example audit app uses `limit` and `offset`
  to implement paging for all audit app queries automatically."
  nil)

(def ^:private resolve-internal-query-fn-lock (Object.))

(defn- resolve-internal-query-fn
  "Returns the varr for the internal query fn."
  [qualified-fn-str]
  (let [[ns-str] (str/split qualified-fn-str #"/")]
    (or
     ;; resolve if already available...
     (locking resolve-internal-query-fn-lock
       (resolve (symbol qualified-fn-str))
       ;; if not, load the namespace...
       (require (symbol ns-str))
       ;; ...then try resolving again
       (resolve (symbol qualified-fn-str)))
     ;; failing that, throw an Exception
     (throw
      (Exception.
       (str (tru "Unable to run internal query function: cannot resolve {0}"
                 qualified-fn-str)))))))

(s/defn ^:private process-internal-query
  [{qualified-fn-str :fn, args :args, :as query} :- InternalQuery rff context]
  ;; Make sure current user is a superuser
  (api/check-superuser)
  ;; Make sure audit app is enabled (currently the only use case for internal queries). We can figure out a way to
  ;; allow non-audit-app queries if and when we add some
  (when-not (metastore/enable-audit-app?)
    (throw (Exception. (str (tru "Audit App queries are not enabled on this instance.")))))
  ;;now resolve the query
  (let [fn-varr (resolve-internal-query-fn qualified-fn-str)]
    ;; Make sure this is actually allowed to be a internal query fn & has the results metadata we'll need
    (when-not (:internal-query-fn (meta fn-varr))
      (throw (Exception. (str (tru "Invalid internal query function: {0} is not marked as an ^:internal-query-fn"
                                   qualified-fn-str)))))
    (let [{:keys [cols rows]} (binding [*additional-query-params* (dissoc query :fn :args)]
                                (apply @fn-varr args))
          metadata            {:cols cols}]
      (context/reducef rff context metadata rows))))

(defn handle-internal-queries
  "Middleware that handles `internal` type queries."
  [qp]
  (fn [{query-type :type, :as query} xform context]
    (if (= :internal (keyword query-type))
      (process-internal-query query xform context)
      (qp query xform context))))
