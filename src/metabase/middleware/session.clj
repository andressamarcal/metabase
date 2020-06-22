(ns metabase.middleware.session
  "Ring middleware related to session (binding current user and permissions)."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [java-time :as t]
            [metabase
             [config :as config]
             [db :as mdb]
             [util :as u]]
            [metabase.api.common :refer [*current-user* *current-user-id* *current-user-permissions-set* *is-superuser?*]]
            [metabase.core.initialization-status :as init-status]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.middleware
             [misc :as mw.misc]
             [util :as mw.util]]
            [metabase.models
             [session :refer [Session]]
             [user :as user :refer [User]]]
            [metabase.util
             [date-2 :as u.date]
             [i18n :refer [deferred-trs tru]]]
            [ring.util.response :as resp]
            [schema.core :as s]
            [toucan.db :as db])
  (:import [java.sql Connection PreparedStatement]
           java.time.temporal.Temporal
           java.util.UUID))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                    Util Fns                                                    |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- wrap-body-if-needed
  "You can't add a cookie (by setting the `:cookies` key of a response) if the response is an unwrapped JSON response;
  wrap `response` if needed."
  [response]
  (if (and (map? response) (contains? response :body))
    response
    {:body response, :status 200}))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                        Setting/Clearing Cookie Util Fns                                        |
;;; +----------------------------------------------------------------------------------------------------------------+

;; How do authenticated API requests work? Metabase first looks for a cookie called `metabase.SESSION`. This is the
;; normal way of doing things; this cookie gets set automatically upon login. `metabase.SESSION` is an HttpOnly
;; cookie and thus can't be viewed by FE code.
;;
;; If that cookie is isn't present, we look for the `metabase.SESSION_ID`, which is the old session cookie set in
;; 0.31.x and older. Unlike `metabase.SESSION`, this cookie was set directly by the frontend and thus was not
;; HttpOnly; for 0.32.x we'll continue to accept it rather than logging every one else out on upgrade. (We've
;; switched to a new Cookie name for 0.32.x because the new cookie includes a `path` attribute, thus browsers consider
;; it to be a different Cookie; Ring cookie middleware does not handle multiple cookies with the same name.)
;;
;; Finally we'll check for the presence of a `X-Metabase-Session` header. If that isn't present, you don't have a
;; Session ID and thus are definitely not authenticated

(def ^:private ^String metabase-session-cookie          "metabase.SESSION")
(def ^:private ^String metabase-embedded-session-cookie "metabase.EMBEDDED_SESSION")
(def ^:private ^String anti-csrf-token-header           "x-metabase-anti-csrf-token")

(defn- clear-cookie [response cookie-name]
  (resp/set-cookie response cookie-name nil {:expires "Thu, 1 Jan 1970 00:00:00 GMT", :path "/"}))

(defn clear-session-cookie
  "Add a header to `response` to clear the current Metabase session cookie."
  [response]
  (reduce clear-cookie (wrap-body-if-needed response) [metabase-session-cookie metabase-embedded-session-cookie]))

(defmulti set-session-cookie
  "Add an appropriate cookie to persist a newly created Session to `response`."
  {:arglists '([response session])}
  (fn [_ {session-type :type}] session-type))

(defmethod set-session-cookie :default
  [_ session]
  (throw (ex-info (str (tru "Invalid session. Expected an instance of Session."))
           {:session session})))

(s/defmethod set-session-cookie :normal
  [response, {session-uuid :id} :- {:id (s/cond-pre UUID u/uuid-regex), s/Keyword s/Any}]
  (let [response       (wrap-body-if-needed response)
        cookie-options (merge
                        {:same-site config/mb-session-cookie-samesite
                         :http-only true
                         ;; TODO - we should set `site-path` as well. Don't want to enable this yet so we don't end
                         ;; up breaking things
                         :path      "/" #_ (site-path)}
                        ;; If the env var `MB_SESSION_COOKIES=true`, do not set the `Max-Age` directive; cookies
                        ;; with no `Max-Age` and no `Expires` directives are session cookies, and are deleted when
                        ;; the browser is closed
                        ;;
                        ;; See https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#Session_cookies
                        (when-not (config/config-bool :mb-session-cookies)
                          ;; max-session age-is in minutes; Max-Age= directive should be in seconds
                          {:max-age (* 60 (config/config-int :max-session-age))})
                        ;; If the authentication request request was made over HTTPS (hopefully always except for
                        ;; local dev instances) add `Secure` attribute so the cookie is only sent over HTTPS.
                        (if (= (mw.util/request-protocol mw.misc/*request*) :https)
                          {:secure true}
                          (when (= config/mb-session-cookie-samesite :none)
                            (log/warn
                             (str (deferred-trs "Session cookie's SameSite is configured to \"None\", but site is ")
                                  (deferred-trs "served over an insecure connection. Some browsers will reject ")
                                  (deferred-trs "cookies under these conditions. ")
                                  (deferred-trs "https://www.chromestatus.com/feature/5633521622188032"))))))]
    (resp/set-cookie response metabase-session-cookie (str session-uuid) cookie-options)))

(s/defmethod set-session-cookie :full-app-embed
  [response, {session-uuid :id, anti-csrf-token :anti_csrf_token} :- {:id       (s/cond-pre UUID u/uuid-regex)
                                                                      s/Keyword s/Any}]
  (let [response       (wrap-body-if-needed response)
        cookie-options (merge
                        {:http-only true
                         :path      "/"}
                        (when (= (mw.util/request-protocol mw.misc/*request*) :https)
                          {:secure true}))]
    (-> response
        (resp/set-cookie metabase-embedded-session-cookie (str session-uuid) cookie-options)
        (assoc-in [:headers anti-csrf-token-header] anti-csrf-token))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                wrap-session-id                                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

(def ^:private ^String metabase-session-header "x-metabase-session")

(defmulti ^:private wrap-session-id-with-strategy
  "Attempt to add `:metabase-session-id` to `request` based on a specific strategy. Return modified request if
  successful or `nil` if we should try another strategy."
  {:arglists '([strategy request])}
  (fn [strategy _]
    strategy))

(defmethod wrap-session-id-with-strategy :embedded-cookie
  [_ {:keys [cookies headers], :as request}]
  (when-let [session (get-in cookies [metabase-embedded-session-cookie :value])]
    (when-let [anti-csrf-token (get headers anti-csrf-token-header)]
      (assoc request :metabase-session-id session, :anti-csrf-token anti-csrf-token))))

(defmethod wrap-session-id-with-strategy :normal-cookie
  [_ {:keys [cookies], :as request}]
  (when-let [session (get-in cookies [metabase-session-cookie :value])]
    (when (seq session)
      (assoc request :metabase-session-id session))))

(defmethod wrap-session-id-with-strategy :header
  [_ {:keys [headers], :as request}]
  (when-let [session (get headers metabase-session-header)]
    (when (seq session)
      (assoc request :metabase-session-id session))))

(defmethod wrap-session-id-with-strategy :best
  [_ request]
  (some
   (fn [strategy]
     (wrap-session-id-with-strategy strategy request))
   [:embedded-cookie :normal-cookie :header]))

(defn wrap-session-id
  "Middleware that sets the `:metabase-session-id` keyword on the request if a session id can be found.
   We first check the request :cookies for `metabase.SESSION`, then if no cookie is found we look in the http headers
  for `X-METABASE-SESSION`. If neither is found then then no keyword is bound to the request."
  [handler]
  (fn [request respond raise]
    (let [request (or (wrap-session-id-with-strategy :best request)
                      request)]
      (handler request respond raise))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                              wrap-current-user-id                                              |
;;; +----------------------------------------------------------------------------------------------------------------+

;; The below code is optimized a bit to avoid recompiling Toucan -> HoneySQL -> SQL on every request. This code is for
;; basically every API request so optimizing a bit here shaves a solid half millisecond from each API calll
(defn- oldest-session-creation-date [session-type]
  (let [session-max-age-minutes (config/config-int (case session-type
                                                     :normal         :max-session-age
                                                     :full-app-embed :embed-max-session-age))]
    (sql.qp/add-interval-honeysql-form (mdb/db-type) :%now (- session-max-age-minutes) :minute)))

(defn- fetch-session-honeysql [session-type]
  {:select    [:session.user_id :user.is_superuser]
   :from      [[Session :session]]
   :left-join [[User :user] [:= :session.user_id :user.id]]
   :where     [:and
               [:= :session.id "?"]
               [:> :session.created_at (oldest-session-creation-date :normal)]
               [:= :session.anti_csrf_token (case session-type
                                              :normal         nil
                                              :full-app-embed "?")]
               [:= :user.is_active true]]
   :limit     1})

(defn- fetch-session-sql* [session-type]
  (first (db/honeysql->sql (fetch-session-honeysql session-type))))

(def ^:private ^{:arglists '([session-type])} fetch-session-sql (memoize fetch-session-sql*))

(defn- prepared-statement ^PreparedStatement [^Connection connection, ^String sql]
  (jdbc/prepare-statement connection sql {:result-type :forward-only
                                          :concurrency :read-only
                                          :fetch-size  1
                                          :max-rows    1
                                          :cursors     :close
                                          :return-keys false}))

(defn- fetch-session [sql [session-id anti-csrf-token]]
  (with-open [conn      (jdbc/get-connection (db/connection))
              statement (prepared-statement conn sql)]
    (.setString statement 1 session-id)
    (when anti-csrf-token
      (.setString statement 2 anti-csrf-token))
    (with-open [result-set (.executeQuery statement)]
      (when (.next result-set)
        {:metabase-user-id (.getInt result-set 1)
         :is-superuser?    (.getBoolean result-set 2)}))))

(defn- session-with-id [session-id anti-csrf-token]
  (if (seq anti-csrf-token)
    (fetch-session (fetch-session-sql :full-app-embed) [session-id anti-csrf-token])
    (fetch-session (fetch-session-sql :normal)         [session-id])))

(s/defn ^:private session-expired?
  ([session]
   (session-expired? session (config/config-int :max-session-age)))

  ([{created-at :created_at} :- {(s/optional-key :created_at) (s/maybe Temporal), s/Keyword s/Any}
    max-age-minutes          :- s/Int]
   (or
    (not created-at)
    (u.date/older-than? created-at (t/minutes max-age-minutes)))))

(defn- current-user-info-for-session
  "Return User ID and superuser status for Session with `session-id` if it is valid and not expired."
  [session-id anti-csrf-token]
  (when (and session-id (init-status/complete?))
    (when-let [session (db/select-one Session :id session-id)]
      (when-not (session-expired? session)
        (session-with-id session-id anti-csrf-token)))))

(defn- wrap-current-user-id* [{:keys [metabase-session-id anti-csrf-token], :as request}]
  (merge request (current-user-info-for-session metabase-session-id anti-csrf-token)))

(defn wrap-current-user-id
  "Add `:metabase-user-id` to the request if a valid session token was passed."
  [handler]
  (fn [request respond raise]
    (handler (wrap-current-user-id* request) respond raise)))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                               bind-current-user                                                |
;;; +----------------------------------------------------------------------------------------------------------------+

(def ^:private current-user-fields
  (into [User] user/admin-or-self-visible-columns))

(defn- find-user [user-id]
  (when user-id
    (db/select-one current-user-fields, :id user-id)))

(defn superuser?
  "Is User with `user-id` a superuser?"
  [user-id]
  (when user-id
    (db/select-one-field :is_superuser User :id user-id)))

(defn do-with-current-user
  "Impl for `with-current-user`."
  [current-user-id superuser? thunk]
  (binding [*current-user-id*              current-user-id
            *is-superuser?*                (boolean superuser?)
            *current-user*                 (delay (find-user current-user-id))
            *current-user-permissions-set* (delay (some-> current-user-id user/permissions-set))]
    (thunk)))

(defmacro with-current-user
  "Execute code in body with User with `current-user-id` bound as the current user."
  {:style/indent 1}
  [current-user-id & body]
  `(let [user-id# ~current-user-id]
     (do-with-current-user user-id# (superuser? user-id#) (fn [] ~@body))))

(defmacro ^:private with-current-user-for-request
  [request & body]
  `(let [request# ~request]
     (do-with-current-user (:metabase-user-id request#) (:is-superuser? request#) (fn [] ~@body))))

(defn bind-current-user
  "Middleware that binds `metabase.api.common/*current-user*`, `*current-user-id*`, `*is-superuser?*`, and
  `*current-user-permissions-set*`.

  *  `*current-user-id*`             int ID or nil of user associated with request
  *  `*current-user*`                delay that returns current user (or nil) from DB
  *  `*is-superuser?*`               Boolean stating whether current user is a superuser.
  *  `current-user-permissions-set*` delay that returns the set of permissions granted to the current user from DB"
  [handler]
  (fn [request respond raise]
    (with-current-user-for-request request
      (handler request respond raise))))
