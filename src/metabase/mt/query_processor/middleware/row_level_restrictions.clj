(ns metabase.mt.query-processor.middleware.row-level-restrictions
  (:require [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.models
             [card :refer [Card]]
             [database :as database]
             [table :refer [Table]]]
            [metabase.query-processor.middleware.log :as log-query]
            [schema.core :as s]))

;; This is the magic glue in between a user supplied query and connecting that to another question via the group table
;; access policy. Information needed to make this decision
;;
;; 1 - Currently logged in user (not currently included in the QueryContext)
;; 2 - User's permission for the table given in `query` (not currently included in the Query Context)
;; 3 - User attributes of the user executing the `query` (Included in the QueryContext, not recognized in MBQL yet)
;; 4 - If segmented, resolve the table + user to a group table access policy and associated question (TODO)
;; 5 - Swap the original table for the resolved GTAP question
(defn- gtap-card-for-table [query]
  (when (and (= 50 (get-in query [:user :login_attributes :cat]))
             (=  "VENUES" (:name (Table (get-in query [:query :source-table])))))
    (str "card__" (u/get-id (Card :name "magic")))))

(defn- login-attr->template-tag [[attr-name attr-value]]
  {:type "category", :value attr-value, :target ["variable" ["template-tag" (name attr-name)]]})

(s/defn apply-row-level-permissions
  [qp]
  (fn [query]
    (if-let [gtap-card (gtap-card-for-table query)]
      (-> query
          (assoc :database database/virtual-id
                 :type     :query)
          (update :query assoc :source-table gtap-card)
          (update :parameters into (map login-attr->template-tag (get-in query [:user :login_attributes])))
          qp)
      (qp query))))

(defn- vec-index-of [pred coll]
  (reduce (fn [new-pipeline idx]
            (if (pred (get coll idx))
              (reduced idx)
              nil)) nil (range 0 (count coll))))

(defn- inject-row-level-permissions-middleware
  [query-pipeline-vars]
  (if-let [resolve-index (and (not-any? #(= #'apply-row-level-permissions %) query-pipeline-vars)
                              (vec-index-of #(= % #'log-query/log-initial-query) query-pipeline-vars))]
    (vec (concat
          (subvec query-pipeline-vars 0 (inc resolve-index))
          [#'apply-row-level-permissions]
          (subvec query-pipeline-vars (inc resolve-index))))
    query-pipeline-vars))

(defn update-qp-pipeline-for-mt
  "Update the query pipeline atom to include the row level restrictions middleware. Intended to be called on startup."
  []
  (swap! qp/pipeline-functions inject-row-level-permissions-middleware))
