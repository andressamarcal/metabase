(ns metabase.middleware.security-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [metabase.middleware.security :as mw.security]
            [metabase.public-settings :as public-settings]
            [metabase.test.util :as tu]))

(defn- csp-frame-ancestors-directive
  []
  (-> (mw.security/security-headers)
      (get "Content-Security-Policy")
      (str/split #"; *")
      (as-> xs (filter #(str/starts-with? % "frame-ancestors ") xs))
      first))

(deftest csp-header-frame-ancestor-tests
  (testing "Frame ancestors from `embedding-app-origin` setting"
    (let [multiple-ancestors "https://*.metabase.com http://metabase.internal"]
      (tu/with-temporary-setting-values [enable-embedding     true
                                         embedding-app-origin multiple-ancestors]
        (is (= (str "frame-ancestors " multiple-ancestors)
               (csp-frame-ancestors-directive))))))

  (testing "Frame ancestors is 'none' for nil `embedding-app-origin`"
    (tu/with-temporary-setting-values [enable-embedding     true
                                       embedding-app-origin nil]
      (is (= "frame-ancestors 'none'"
             (csp-frame-ancestors-directive)))))

  (testing "Frame ancestors is 'none' if embedding is disabled"
    (tu/with-temporary-setting-values [enable-embedding false
                                       embedding-app-origin "https: http:"]
      (is (= "frame-ancestors 'none'"
          (csp-frame-ancestors-directive))))))
