(ns metabase.mt.query-processor.middleware.util
  (:require [metabase.query-processor :as qp]))

(defn- vec-index-of [pred coll]
  (reduce (fn [new-pipeline idx]
            (if (pred (get coll idx))
              (reduced idx)
              nil)) nil (range 0 (count coll))))


(defn- inject-middleware
  [qp-pipeline-vars inject-after-middleware middleware-to-inject]
  (if-let [resolve-index (and (not-any? #(= middleware-to-inject %) qp-pipeline-vars)
                              (vec-index-of #(= % inject-after-middleware) qp-pipeline-vars))]
    (vec (concat
          (subvec qp-pipeline-vars 0 (inc resolve-index))
          [middleware-to-inject]
          (subvec qp-pipeline-vars (inc resolve-index))))
    qp-pipeline-vars))

(defn update-qp-pipeline
  "Updates the query processor pipeline, adding `middleware-to-inject` after `inject-after-middleware`"
  [inject-after-middleware middleware-to-inject]
  (swap! qp/pipeline-functions inject-middleware inject-after-middleware middleware-to-inject))
