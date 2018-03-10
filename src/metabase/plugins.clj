(ns metabase.plugins
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [dynapath.util :as dynapath]
            [metabase
             [config :as config]
             [util :as u]])
  (:import [java.net URL URLClassLoader]))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          Adding JARs to the Classpath                                          |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- plugins-dir
  "The Metabase plugins directory. This defaults to `plugins/` in the same directory as `metabase.jar`, but can be
  configured via the env var `MB_PLUGINS_DIR`."
  ^java.io.File []
  (let [dir (io/file (or (config/config-str :mb-plugins-dir)
                         (str (System/getProperty "user.dir") "/plugins")))]
    (when (and (.isDirectory dir)
               (.canRead dir))
      dir)))


(defn- add-jar-to-classpath!
  "Dynamically add a JAR file to the classpath. See also
  http://stackoverflow.com/questions/60764/how-should-i-load-jars-dynamically-at-runtime/60766#60766"
  [^java.io.File jar-file]
  (let [sysloader (ClassLoader/getSystemClassLoader)]
    (dynapath/add-classpath-url sysloader (.toURL (.toURI jar-file)))))

(defn load-plugins!
  "Dynamically add any JARs in the `plugins-dir` to the classpath. This is used for things like custom plugins or the
  Oracle JDBC driver, which cannot be shipped alongside Metabase for licensing reasons."
  []
  (when-let [^java.io.File dir (plugins-dir)]
    (log/info (format "Adding JARs to classpath in directory %s..." dir))
    (doseq [^java.io.File file (.listFiles dir)
            :when (and (.isFile file)
                       (.canRead file)
                       (re-find #"\.jar$" (.getPath file)))]
      (log/info (u/format-color 'magenta "Adding JAR to classpath %s... %s" file (u/emoji "🔌")))
      (add-jar-to-classpath! file))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                            Running Plugin Setup Fns                                            |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn setup-plugins!
  "Look for any namespaces on the classpath with the pattern `metabase.*plugin-setup` For each matching namespace, load
  it. If the namespace has a function named `-init-plugin!`, call that function with no arguments.

  This is intended as a startup hook for Metabase plugins to run any startup logic that needs to be done. This
  function is normally called once in Metabase startup, after `load-plugins!` runs above (which simply adds JARs to
  the classpath in the `plugins` directory.)"
  []
  ;; find each namespace ending in `plugin-setup`
  (doseq [ns-symb @u/metabase-namespace-symbols
          :when   (re-find #"plugin-setup$" (name ns-symb))]
    ;; load the matching ns
    (log/info (u/format-color 'magenta "Loading plugin setup namespace %s... %s" (name ns-symb) (u/emoji "🔌")))
    (require ns-symb)
    ;; now look for a fn in that namespace called `-init-plugin!`. If it exists, call it
    (when-let [init-fn-var (ns-resolve ns-symb '-init-plugin!)]
      (log/info (u/format-color 'magenta "Running plugin init fn %s/-init-plugin!... %s" (name ns-symb) (u/emoji "🔌")))
      (init-fn-var))))
