(ns metabase.test.serialization
  (:require [metabase.models
             [card :refer [Card]]
             [collection :refer [Collection]]
             [dashboard :refer [Dashboard]]
             [dashboard-card :refer [DashboardCard]]
             [dashboard-card-series :refer [DashboardCardSeries]]
             [database :as database :refer [Database]]
             [field :refer [Field]]
             [metric :refer [Metric]]
             [pulse :refer [Pulse]]
             [segment :refer [Segment]]
             [table :refer [Table]]
             [user :refer [User]]]
            [metabase.serialization.names :as names]
            [metabase.test.data :as data]
            [toucan.util.test :as tt]))

(defmacro with-world
  "Run test in the context of a minimal Metabase instance connected to our test database."
  [& body]
  `(tt/with-temp* [Collection [{~'collection-id :id} {:name "My Collection"}]
                   Collection [{~'collection-id-nested :id} {:name "My Nested Collection"
                                                             :location (format "/%s/" ~'collection-id)}]
                   Metric     [{~'metric-id :id} {:name "My Metric"
                                                    :table_id (data/id :venues)
                                                          :definition {:source-table (data/id :venues)
                                                                       :aggregation [:sum [:field-id (data/id :venues :price)]]}}]

                   Segment    [{~'segment-id :id} {:name "My Segment"
                                                   :table_id (data/id :venues)
                                                   :definition {:source-table (data/id :venues)
                                                                :filter [:!= [:field-id (data/id :venues :category_id)] nil]}}]
                   Dashboard  [{~'dashboard-id :id} {:name "My Dashboard"
                                                     :collection_id ~'collection-id}]
                   Card       [{~'card-id :id}
                               {:table_id (data/id :venues)
                                :name "My Card"
                                :collection_id ~'collection-id
                                :dataset_query {:type :query
                                                :database (data/id)
                                                :query {:source-table (data/id :venues)
                                                        :aggregation [:sum [:field-id (data/id :venues :price)]]
                                                        :breakout [[:field-id (data/id :venues :category_id)]]}}}]
                   Card       [{~'card-id-root :id}
                               {:table_id (data/id :venues)
                                :name "My Root Card"
                                :dataset_query {:type :query
                                                :database (data/id)
                                                :query {:source-table (data/id :venues)}}}]
                   Card       [{~'card-id-nested :id}
                               {:table_id (data/id :venues)
                                :name "My Nested Card"
                                :collection_id ~'collection-id
                                :dataset_query {:type :query
                                                :database (data/id)
                                                :query {:source-table (str "card__" ~'card-id)}}}]
                   DashboardCard       [{~'dashcard-id :id}
                                        {:dashboard_id ~'dashboard-id
                                         :card_id ~'card-id}]
                   DashboardCardSeries [~'_ {:dashboardcard_id ~'dashcard-id
                                             :card_id ~'card-id
                                             :position 0}]
                   DashboardCardSeries [~'_ {:dashboardcard_id ~'dashcard-id
                                             :card_id ~'card-id-nested
                                             :position 1}]]
     ~@body))

;; Don't memoize as IDs change in each `with-world` context
(alter-var-root #'names/path->context (fn [_] #'names/path->context*))
