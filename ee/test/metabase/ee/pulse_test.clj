(ns metabase.ee.pulse-test
  (:require [clojure.test :refer :all]
            [metabase
             [email-test :as email-test]
             [pulse :as pulse]
             [query-processor :as qp]
             [query-processor-test :as qp.test]]
            [metabase.email.messages :as messages]
            [metabase.models
             [card :refer [Card]]
             [pulse :as models.pulse :refer [Pulse]]
             [pulse-card :refer [PulseCard]]
             [pulse-channel :refer [PulseChannel]]
             [pulse-channel-recipient :refer [PulseChannelRecipient]]]
            [metabase.mt.test-util :as mt.tu]
            [metabase.pulse.test-util :as pulse.tu]
            [metabase.test
             [data :as data]
             [util :as tu]]
            [metabase.test.data.users :as users]
            [toucan.util.test :as tt]))

(deftest sandboxed-pulse-test
  (testing "Pulses should get sent with the row-level restrictions of the User that created them."
    (letfn [(send-pulse-created-by-user! [user-kw]
              (mt.tu/with-gtaps {:gtaps      {:venues {:query      (data/mbql-query venues)
                                                       :remappings {:cat ["variable" [:field-id (data/id :venues :category_id)]]}}}
                                 :attributes {"cat" 50}}
                (tt/with-temp Card [card {:dataset_query (data/mbql-query venues {:aggregation [[:count]]})}]
                  ;; `with-gtaps` binds the current test user; we don't want that falsely affecting results
                  (users/with-test-user nil
                    (pulse.tu/send-pulse-created-by-user! user-kw card)))))]
      (is (= [[100]]
             (send-pulse-created-by-user! :crowberto)))
      (is (= [[10]]
             (send-pulse-created-by-user! :rasta))))))

(deftest e2e-sandboxed-pulse-test
  (testing "Sending Pulses w/ sandboxing, end-to-end"
    (mt.tu/with-gtaps {:gtaps {:venues {:query (data/mbql-query venues
                                                 {:filter [:= $price 3]})}}}
      (let [query (data/mbql-query venues
                    {:aggregation [[:count]]
                     :breakout    [$price]})]
        (is (= [[3 13]]
               (qp.test/formatted-rows [int int]
                 (users/with-test-user :rasta
                   (qp/process-query query))))
            "Basic sanity check: make sure the query is properly set up to apply GTAPs")
        (tt/with-temp* [Card                  [pulse-card {:dataset_query query}]
                        Pulse                 [pulse {:name "Test Pulse"}]
                        PulseCard             [_ {:pulse_id (:id pulse), :card_id (:id pulse-card)}]
                        PulseChannel          [pc {:channel_type :email
                                                   :pulse_id     (:id pulse)
                                                   :enabled      true}]
                        PulseChannelRecipient [_ {:pulse_channel_id (:id pc)
                                                  :user_id          (users/user->id :rasta)}]]
          (tu/with-temporary-setting-values [email-from-address "metamailman@metabase.com"]
            (email-test/with-fake-inbox
              (with-redefs [messages/render-pulse-email (fn [_ _ [{:keys [result]}]]
                                                          [{:result (qp.test/formatted-rows [int int]
                                                                      result)}])]
                (pulse/send-pulse! pulse))
              (is (= {"rasta@metabase.com" [{:from    "metamailman@metabase.com"
                                             :to      ["rasta@metabase.com"]
                                             :subject "Pulse: Test Pulse"
                                             :body    [{:result [[3 13]]}]}]}
                     @email-test/inbox)
                  "GTAPs should apply to Pulses — they should get the same results as if running that query normally"))))))))
