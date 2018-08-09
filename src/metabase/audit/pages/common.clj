(ns metabase.audit.pages.common
  "Shared functions used by audit internal queries across different namespaces."
  (:require [honeysql.core :as hsql]
            [metabase.util.honeysql-extensions :as hx]))

(defn user-full-name
  "HoneySQL to grab the full name of a User.

     (user-full-name :u) ;; -> 'Cam Saul'"
  [user-table]
  (hx/concat (hsql/qualify user-table :first_name)
             (hx/literal " ")
             (hsql/qualify user-table :last_name)))
