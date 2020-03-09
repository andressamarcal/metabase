(ns metabase-enterprise.serialization.serialize-test
  (:require [clojure.string :as str]
            [expectations :refer :all]
            [metabase-enterprise.serialization
             [serialize :as serialize :refer :all]
             [test-util :as ts]]
            [metabase.models :refer [Card Collection Dashboard Database Field Metric Segment Table]]))

(defn- all-ids-are-fully-qualified-names?
  [m]
  (every? string? (for [[k v] m
                        :when (and v (-> k name (str/ends-with? "_id")))]
                    v)))

(defn- all-mbql-ids-are-fully-qualified-names?
  [[_ & ids]]
  (every? string? ids))

(defn- valid-serialization?
  [s]
  (->> s
       (tree-seq coll? identity)
       (filter (some-fn map? #'serialize/mbql-entity-reference?))
       (every? (fn [x]
                 (if (map? x)
                   (all-ids-are-fully-qualified-names? x)
                   (all-mbql-ids-are-fully-qualified-names? x))))))

(def ^:private test-serialization (comp valid-serialization? serialize))

(expect
  (ts/with-world
    (test-serialization (Card card-id))))

(expect
  (ts/with-world
    (test-serialization (Metric metric-id))))

(expect
  (ts/with-world
    (test-serialization (Segment segment-id))))

(expect
  (ts/with-world
    (test-serialization (Collection collection-id))))

(expect
  (ts/with-world
    (test-serialization (Dashboard dashboard-id))))

(expect
  (ts/with-world
    (test-serialization (Table table-id))))

(expect
  (ts/with-world
    (test-serialization (Field numeric-field-id))))

(expect
  (ts/with-world
    (test-serialization (Database db-id))))
