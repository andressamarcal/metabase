(ns metabase.middleware.security
  "Ring middleware for adding security-related headers to API responses."
  (:require [clojure.string :as str]
            [metabase
             [config :as config]
             [public-settings :as public-settings]]
            [metabase.middleware.util :as middleware.u]
            [metabase.models.setting :refer [defsetting]]
            [metabase.util
             [date :as du]
             [i18n :as ui18n :refer [tru]]]))

(defn- cache-prevention-headers
  "Headers that tell browsers not to cache a response."
  []
  {"Cache-Control" "max-age=0, no-cache, must-revalidate, proxy-revalidate"
   "Expires"        "Tue, 03 Jul 2001 06:00:00 GMT"
   "Last-Modified"  (du/format-date :rfc822)})

 (defn- cache-far-future-headers
   "Headers that tell browsers to cache a static resource for a long time."
   []
   {"Cache-Control" "public, max-age=31536000"})

(def ^:private ^:const strict-transport-security-header
  "Tell browsers to only access this resource over HTTPS for the next year (prevent MTM attacks). (This only applies if
  the original request was HTTPS; if sent in response to an HTTP request, this is simply ignored)"
  {"Strict-Transport-Security" "max-age=31536000"})

(def ^:private content-security-policy-header
  "`Content-Security-Policy` header. See https://content-security-policy.com for more details."
  {"Content-Security-Policy"
   (str/join
    (for [[k vs] {:default-src ["'none'"]
                  :script-src  ["'unsafe-inline'"
                                "'unsafe-eval'"
                                "'self'"
                                "https://maps.google.com"
                                "https://apis.google.com"
                                "https://www.google-analytics.com" ; Safari requires the protocol
                                "https://*.googleapis.com"
                                "*.gstatic.com"
                                (when config/is-dev?
                                  "localhost:8080")]
                  :child-src   ["'self'"
                                ;; TODO - double check that we actually need this for Google Auth
                                "https://accounts.google.com"]
                  :style-src   ["'unsafe-inline'"
                                "'self'"
                                "fonts.googleapis.com"]
                  :font-src    ["'self'"
                                "fonts.gstatic.com"
                                "themes.googleusercontent.com"
                                (when config/is-dev?
                                  "localhost:8080")]
                  :img-src     ["*"
                                "'self' data:"]
                  :connect-src ["'self'"
                                "metabase.us10.list-manage.com"
                                (when config/is-dev?
                                  "localhost:8080 ws://localhost:8080")]}]
      (format "%s %s; " (name k) (apply str (interpose " " vs)))))})

(defn- embedding-app-origin
  []
  (when (and (public-settings/enable-embedding) (public-settings/embedding-app-origin))
    (public-settings/embedding-app-origin)))

(defn- content-security-policy-header-with-frame-ancestors
  [allow-iframes?]
  (update content-security-policy-header
          "Content-Security-Policy"
          #(format "%s frame-ancestors %s;" % (if allow-iframes? "*" (or (embedding-app-origin) "'none'")))))

(defsetting ssl-certificate-public-key
  (str (tru "Base-64 encoded public key for this site's SSL certificate.")
       (tru "Specify this to enable HTTP Public Key Pinning.")
       (tru "See {0} for more information." "http://mzl.la/1EnfqBf")))
;; TODO - it would be nice if we could make this a proper link in the UI; consider enabling markdown parsing

#_(defn- public-key-pins-header []
  (when-let [k (ssl-certificate-public-key)]
    {"Public-Key-Pins" (format "pin-sha256=\"base64==%s\"; max-age=31536000" k)}))

(defn security-headers
  "Fetch a map of security headers that should be added to a response based on the passed options."
  [& {:keys [allow-iframes? allow-cache?]
      :or   {allow-iframes? false, allow-cache? false}}]
  (merge
   (if allow-cache?
     (cache-far-future-headers)
     (cache-prevention-headers))
   strict-transport-security-header
   (content-security-policy-header-with-frame-ancestors allow-iframes?)
   #_(public-key-pins-header)
   (when-not allow-iframes?
     ;; Tell browsers not to render our site as an iframe (prevent clickjacking)
     {"X-Frame-Options"                 (if (embedding-app-origin)
                                          (format "ALLOW-FROM %s" (embedding-app-origin))
                                          "DENY")})
   { ;; Tell browser to block suspected XSS attacks
    "X-XSS-Protection"                  "1; mode=block"
    ;; Prevent Flash / PDF files from including content from site.
    "X-Permitted-Cross-Domain-Policies" "none"
    ;; Tell browser not to use MIME sniffing to guess types of files -- protect against MIME type confusion attacks
    "X-Content-Type-Options"            "nosniff"}))

(defn- add-security-headers* [request response]
  (update response :headers merge (security-headers
                                   :allow-iframes? ((some-fn middleware.u/public? middleware.u/embed?) request)
                                   :allow-cache?   (middleware.u/cacheable? request))))

(defn add-security-headers
  "Add HTTP security and cache-busting headers."
  [handler]
  (fn [request respond raise]
    (handler
     request
     (comp respond (partial add-security-headers* request))
     raise)))
