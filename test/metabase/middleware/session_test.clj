(ns metabase.middleware.session-test
  (:require [clojure.string :as str]
            [environ.core :as env]
            [expectations :refer [expect]]
            [metabase.api.common :refer [*current-user* *current-user-id*]]
            [metabase.middleware
             [misc :as mw.misc]
             [session :as mw.session]]
            [metabase.models.session :refer [Session]]
            [metabase.test.data.users :as test-users]
            [ring.mock.request :as mock]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

;;; ----------------------------------------------- set-session-cookie -----------------------------------------------

(def ^:private session-cookie @#'mw.session/metabase-session-cookie)

(def ^:private test-uuid #uuid "092797dd-a82a-4748-b393-697d7bb9ab65")

(def ^:private test-session
  {:id test-uuid, :type :normal})

;; let's see whether we can set a Session cookie using the default options
(expect
  ;; should unset the old SESSION_ID if it's present
  {session-cookie
   {:value     "092797dd-a82a-4748-b393-697d7bb9ab65"
    :same-site :lax
    :http-only true
    :path      "/"
    :max-age   1209600}}
  (-> (mw.session/set-session-cookie {} test-session)
      :cookies))

;; if `MB_SESSION_COOKIES=true` we shouldn't set a `Max-Age`
(expect
  {:value     "092797dd-a82a-4748-b393-697d7bb9ab65"
   :same-site :lax
   :http-only true
   :path      "/"}
  (let [env env/env]
    (with-redefs [env/env (assoc env :mb-session-cookies "true")]
      (-> (mw.session/set-session-cookie {} test-session)
          (get-in [:cookies session-cookie])))))

;; if request is an HTTPS request then we should set `:secure true`. There are several different headers we check for
;; this. Make sure they all work.
(defn- secure-cookie-for-headers? [headers]
  (binding [mw.misc/*request* {:headers headers}]
    (-> (mw.session/set-session-cookie {} test-session)
        (get-in [:cookies session-cookie :secure])
        boolean)))

(expect true  (secure-cookie-for-headers? {"x-forwarded-proto" "https"}))
(expect false (secure-cookie-for-headers? {"x-forwarded-proto" "http"}))

(expect true  (secure-cookie-for-headers? {"x-forwarded-protocol" "https"}))
(expect false (secure-cookie-for-headers? {"x-forwarded-protocol" "http"}))

(expect true  (secure-cookie-for-headers? {"x-url-scheme" "https"}))
(expect false (secure-cookie-for-headers? {"x-url-scheme" "http"}))

(expect true  (secure-cookie-for-headers? {"x-forwarded-ssl" "on"}))
(expect false (secure-cookie-for-headers? {"x-forwarded-ssl" "off"}))

(expect true  (secure-cookie-for-headers? {"front-end-https" "on"}))
(expect false (secure-cookie-for-headers? {"front-end-https" "off"}))

(expect true  (secure-cookie-for-headers? {"origin" "https://mysite.com"}))
(expect false (secure-cookie-for-headers? {"origin" "http://mysite.com"}))


;;; ------------------------------------- tests for full-app embedding sessions --------------------------------------

(def ^:private embedded-session-cookie @#'mw.session/metabase-embedded-session-cookie)
(def ^:private anti-csrf-token-header @#'mw.session/anti-csrf-token-header)

(def ^:private test-anti-csrf-token "84482ddf1bb178186ed9e1c0b1e05a2d")

(def ^:private test-full-app-embed-session
  {:id               test-uuid
   :anti_csrf_token  test-anti-csrf-token
   :type             :full-app-embed})

;; test that we can set a full-app-embedding session cookie
(expect
  {:body    {}
   :status  200
   :cookies {embedded-session-cookie
             {:value     "092797dd-a82a-4748-b393-697d7bb9ab65"
              :http-only true
              :path      "/"}}
   :headers {anti-csrf-token-header test-anti-csrf-token}}
  (mw.session/set-session-cookie {} test-full-app-embed-session))


;;; ---------------------------------------- TEST wrap-session-id middleware -----------------------------------------

(def ^:private session-header @#'mw.session/metabase-session-header)

;; create a simple example of our middleware wrapped around a handler that simply returns the request
;; this works in this case because the only impact our middleware has is on the request
(defn- wrapped-handler [request]
  ((mw.session/wrap-session-id
    (fn [request respond _] (respond request)))
   request
   identity
   (fn [e] (throw e))))


;; no session-id in the request
(expect
  nil
  (-> (wrapped-handler (mock/request :get "/anyurl") )
      :metabase-session-id))


;; extract session-id from header
(expect
  "foobar"
  (:metabase-session-id
   (wrapped-handler
    (mock/header (mock/request :get "/anyurl") session-header "foobar"))))


;; extract session-id from cookie
(expect
  "cookie-session"
  (:metabase-session-id
   (wrapped-handler
    (assoc (mock/request :get "/anyurl")
      :cookies {session-cookie {:value "cookie-session"}}))))


;; if both header and cookie session-ids exist, then we expect the cookie to take precedence
(expect
  "cookie-session"
  (:metabase-session-id
   (wrapped-handler
    (assoc (mock/header (mock/request :get "/anyurl") session-header "foobar")
           :cookies {session-cookie {:value "cookie-session"}}))))

;; `wrap-session-id` should handle anti-csrf headers they way we'd expect
(expect
  {:anti-csrf-token     "84482ddf1bb178186ed9e1c0b1e05a2d"
   :cookies             {embedded-session-cookie {:value "092797dd-a82a-4748-b393-697d7bb9ab65"}}
   :metabase-session-id "092797dd-a82a-4748-b393-697d7bb9ab65"
   :uri                 "/anyurl"}
  (let [request (-> (mock/request :get "/anyurl")
                    (assoc :cookies {embedded-session-cookie {:value (str test-uuid)}})
                    (assoc-in [:headers anti-csrf-token-header] test-anti-csrf-token))]
    (select-keys (wrapped-handler request) [:anti-csrf-token :cookies :metabase-session-id :uri])))


;;; --------------------------------------- TEST bind-current-user middleware ----------------------------------------

;; make sure the `session-with-id` logic is working correctly
(expect
  {:metabase-user-id (test-users/user->id :lucky), :is-superuser? false}
  ;; for some reason Toucan seems to be busted with models with non-integer IDs and `with-temp` doesn't seem to work
  ;; the way we'd expect :/
  (try
    (tt/with-temp Session [session {:id (str test-uuid), :user_id (test-users/user->id :lucky)}]
      (#'mw.session/session-with-id (str test-uuid) nil))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; superusers should come back as `:is-superuser?`
(expect
  {:metabase-user-id (test-users/user->id :crowberto), :is-superuser? true}
  (try
    (tt/with-temp Session [session {:id (str test-uuid), :user_id (test-users/user->id :crowberto)}]
      (#'mw.session/session-with-id (str test-uuid) nil))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; full-app-embed sessions shouldn't come back if we don't explicitly specifiy the anti-csrf token
(expect
  nil
  (try
    (tt/with-temp Session [session {:id              (str test-uuid)
                                    :user_id         (test-users/user->id :lucky)
                                    :anti_csrf_token test-anti-csrf-token}]
      (#'mw.session/session-with-id (str test-uuid) nil))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; ...but if we do specifiy the token, they should come back
(expect
  {:metabase-user-id (test-users/user->id :lucky), :is-superuser? false}
  (try
    (tt/with-temp Session [session {:id              (str test-uuid)
                                    :user_id         (test-users/user->id :lucky)
                                    :anti_csrf_token test-anti-csrf-token}]
      (#'mw.session/session-with-id (str test-uuid) test-anti-csrf-token))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; (unless the token is wrong)
(expect
  nil
  (try
    (tt/with-temp Session [session {:id              (str test-uuid)
                                    :user_id         (test-users/user->id :lucky)
                                    :anti_csrf_token test-anti-csrf-token}]
      (#'mw.session/session-with-id (str test-uuid) (str/join (reverse test-anti-csrf-token))))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; if we specify an anti-csrf token we shouldn't get back a session without that token
(expect
  nil
  (try
    (tt/with-temp Session [session {:id      (str test-uuid)
                                    :user_id (test-users/user->id :lucky)}]
      (#'mw.session/session-with-id (str test-uuid) test-anti-csrf-token))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; shouldn't fetch expired sessions
(expect
  nil
  (try
    (tt/with-temp Session [session {:id      (str test-uuid)
                                    :user_id (test-users/user->id :lucky)}]
      ;; use low-level `execute!` because updating is normally disallowed for Sessions
      (db/execute! {:update Session, :set {:created_at (java.sql.Date. 0)}, :where [:= :id (str test-uuid)]})
      (#'mw.session/session-with-id (str test-uuid) nil))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; shouldn't fetch sessions for inactive users
(expect
  nil
  (try
    (tt/with-temp Session [session {:id (str test-uuid), :user_id (test-users/user->id :trashbird)}]
      (#'mw.session/session-with-id (str test-uuid) nil))
    (finally
      (db/delete! Session :id (str test-uuid)))))

;; create a simple example of our middleware wrapped around a handler that simply returns our bound variables for users
(defn- user-bound-handler [request]
  ((mw.session/bind-current-user
    (fn [_ respond _]
      (respond
       {:user-id *current-user-id*
        :user    (select-keys @*current-user* [:id :email])})))
   request
   identity
   (fn [e] (throw e))))

(defn- request-with-user-id
  "Creates a mock Ring request with the given user-id applied"
  [user-id]
  (-> (mock/request :get "/anyurl")
      (assoc :metabase-user-id user-id)))


;; with valid user-id
(expect
  {:user-id (test-users/user->id :rasta)
   :user    {:id    (test-users/user->id :rasta)
             :email (:email (test-users/fetch-user :rasta))}}
  (user-bound-handler
   (request-with-user-id (test-users/user->id :rasta))))

;; with invalid user-id (not sure how this could ever happen, but lets test it anyways)
(expect
  {:user-id 0
   :user    {}}
  (user-bound-handler
   (request-with-user-id 0)))
