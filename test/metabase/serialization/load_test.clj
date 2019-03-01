(ns metabase.serialization.load-test
  (:require [expectations :refer :all]
            [metabase.cmd.serialization :refer :all]
            [metabase.models
             [card :refer [Card]]
             [collection :refer [Collection]]
             [dashboard :refer [Dashboard]]
             [dashboard-card :refer [DashboardCard]]
             [database :refer [Database]]
             [field :refer [Field]]
             [metric :refer [Metric]]
             [segment :refer [Segment]]
             [table :refer [Table]]]
            [metabase.serialization.upsert :as upsert]
            [metabase.test.data.users :as test-users]
            [metabase.test.serialization :as ts]
            [metabase.util :as u]
            [toucan.db :as db]))

(defn- delete-directory
  [path]
  (doseq [file (->> path
                    clojure.java.io/file
                    file-seq
                    reverse)]
    (clojure.java.io/delete-file (.getPath file))))

(def ^:private dump-dir "test-dump")

(expect
  (try
    (let [fingerprint (ts/with-world
                        (dump dump-dir (:email (test-users/fetch-user :crowberto)))
                        [[Database db-id]
                         [Table table-id]
                         [Field numeric-field-id]
                         [Field category-field-id]
                         [Collection collection-id]
                         [Collection collection-id-nested]
                         [Metric metric-id]
                         [Segment segment-id]
                         [Dashboard dashboard-id]
                         [Card card-id]
                         [Card card-id-root]
                         [Card card-id-nested]
                         [DashboardCard dashcard-id]])]
      (load dump-dir {:on-error :abort})
      (u/prog1 (every? (fn [[model id]]
                         (#'upsert/select-identical model (model id)))
                       fingerprint)
        (doseq [[model id] fingerprint]
          (db/delete! model :id id))))
    (finally
      (delete-directory dump-dir))))
