(ns metabase.mt.api.util-test
  (:require [expectations :refer [expect]]
            [metabase.mt.api.util :as mt.api.u]
            [metabase.mt.test-util :as mt.tu]
            [metabase.test.data.users :as test.users]))

;; admins should not be classified as segmented users -- enterprise #147
(defn- has-segmented-perms-when-segmented-db-exists? [user-kw]
  (mt.tu/with-copy-of-test-db [db]
    (mt.tu/add-segmented-perms-for-venues-for-all-users-group! db)
    (test.users/with-test-user user-kw
      (mt.api.u/segmented-user?))))

(expect
  (has-segmented-perms-when-segmented-db-exists? :rasta))

(expect
  false
  (has-segmented-perms-when-segmented-db-exists? :crowberto))
