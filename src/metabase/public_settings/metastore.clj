(ns metabase.public-settings.metastore
  "Settings related to checking token validity and accessing the MetaStore."
  (:require [cheshire.core :as json]
            [clojure.core.memoize :as memoize]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [metabase.config :as config]
            [metabase.models.setting :as setting :refer [defsetting]]
            [metabase.util.schema :as su]
            [puppetlabs.i18n.core :refer [trs tru]]
            [schema.core :as s]))

(def ^:private ValidToken
  "Schema for a valid metastore token. Must be 64 lower-case hex characters."
  #"^[0-9a-f]{64}$")

(def store-url
  "URL to the MetaStore. Hardcoded by default but for development purposes you can use a local server. Specify the env
   var `METASTORE_DEV_SERVER_URL`."
  (or
   ;; only enable the changing the store url during dev because we don't want people switching it out in production!
   (when config/is-dev?
     (some-> (env :metastore-dev-server-url)
             ;; remove trailing slashes
             (str/replace  #"/$" "")))
   "https://store.metabase.com"))

(log/info (tru "Using MetaStore URL:") store-url)


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                TOKEN VALIDATION                                                |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- token-status-url [token]
  (when (seq token)
    (format "%s/api/%s/status" store-url token)))

(def ^:private ^:const fetch-token-status-timeout-ms 10000) ; 10 seconds

(def ^:private TokenStatus
  {:valid              s/Bool
   :status             su/NonBlankString
   (s/maybe :features) [su/NonBlankString]})

(s/defn ^:private fetch-token-status :- TokenStatus
  "Fetch info about the validity of `token` from the MetaStore."
  [token :- ValidToken]
  (try
    ;; attempt to query the metastore API about the status of this token. If the request doesn't complete in a
    ;; reasonable amount of time throw a timeout exception
    (deref (future
             (try (some-> (token-status-url token)
                          slurp
                          (json/parse-string keyword))
                  ;; slurp will throw a FileNotFoundException for 404s, so in that case just return an appropriate
                  ;; 'Not Found' message
                  (catch java.io.FileNotFoundException e
                    {:valid false, :status (tru "Unable to validate token.")})
                  ;; if there was any other error fetching the token, log it and return a generic message about the
                  ;; token being invalid. This message will get displayed in the Settings page in the admin panel so
                  ;; we do not want something complicated
                  (catch Throwable e
                    (log/error e (trs "Error fetching token status:"))
                    {:valid false, :status (tru "There was an error checking whether this token was valid.")})))
           fetch-token-status-timeout-ms
           {:valid false, :status (tru "Token validation timed out.")})))

(s/defn ^:private valid-token->features* :- #{su/NonBlankString}
  [token :- ValidToken]
  (log/info (trs "Checking with the MetaStore to see whether {0} is valid..." token))
  (let [{:keys [valid status features]} (fetch-token-status token)]
    ;; if token isn't valid throw an Exception with the `:status` message
    (when-not valid
      (throw (Exception. ^String status)))
    ;; otherwise return the features this token supports
    (set features)))

(def ^:private ^:const valid-token-recheck-interval-ms
  "Amount of time to cache the status of a valid embedding token before forcing a re-check"
  (* 1000 60 60 24)) ; once a day

(def ^:private ^{:arglists '([token])} valid-token->features
  "Check whether `token` is valid. Throws an Exception if not. Returns a set of supported features if it is."
  ;; this is just `valid-token->features*` with some light caching
  (memoize/ttl valid-token->features*
    :ttl/threshold valid-token-recheck-interval-ms))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                             SETTING & RELATED FNS                                              |
;;; +----------------------------------------------------------------------------------------------------------------+

(defsetting premium-embedding-token ; TODO - rename this to premium-features-token?
  (tru "Token for premium features. Go to the MetaStore to get yours!")
  :setter (fn [new-value]
            ;; validate the new value if we're not unsetting it
            (try
              (when (seq new-value)
                (valid-token->features new-value)
                (log/info (trs "Token is valid.")))
              (setting/set-string! :premium-embedding-token new-value)
              (catch Throwable e
                (log/error e (trs "Error setting premium embedding token"))
                (throw (ex-info (.getMessage e) {:status-code 400}))))))

(s/defn ^:private token-features :- #{su/NonBlankString}
  "Get the features associated with the system's premium features token."
  []
  (or (some-> (premium-embedding-token) valid-token->features)
      #{}))

(defn hide-embed-branding?
  "Should we hide the 'Powered by Metabase' attribution on the embedding pages? `true` if we have a valid premium
   embedding token."
  []
  (boolean ((token-features) "embedding")))

(defn enable-whitelabeling?
  "Should we allow full whitelabel embedding (reskinning the entire interface?)"
  []
  (boolean ((token-features) "whitelabel")))

(defn enable-audit-app?
  "Should we allow use of the audit app?"
  []
  (boolean ((token-features) "audit-app")))

(defn enable-sandboxes?
  "Should we enable data sandboxes (row and column-level permissions?"
  []
  (boolean ((token-features) "sandboxes")))

(defn enable-sso?
  "Should we enable SAML/JWT sign-in?"
  []
  (boolean ((token-features) "sso")))
