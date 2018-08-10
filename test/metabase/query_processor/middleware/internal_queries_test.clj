(ns metabase.query-processor.middleware.internal-queries-test
  (:require [expectations :refer [expect]]
            [metabase.test.data.users :as test-users]))

;; Just make sure these queries can actually be ran for now. More tests to come...
(expect
  "completed"
  (-> ((test-users/user->client :crowberto) :post 200 "dataset"
       {:type       :internal
        :fn         "metabase.audit.pages.user-detail/most-viewed-questions"
        :args       [(test-users/user->id :crowberto)]
        ;; havinig an extra unused key shouldn't fail
        :parameters []})
      :status))
