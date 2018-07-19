(ns metabase.mt.integrations.sso-utils
  "Functions shared by the various SSO implementations"
  (:require [metabase.email.messages :as email]
            [metabase.models.user :refer [User]]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan.db :as db])
  (:import java.util.UUID))

(def ^:private UserAttributes
  {:first_name su/NonBlankString
   :last_name su/NonBlankString
   :email su/Email
   :sso_source (s/enum "saml" "jwt")})

(s/defn create-new-sso-user!
  "This function is basically the same thing as the `create-new-google-auth-user` from `metabase.models.user`. We need
  to refactor the `core_user` table structure and the function used to populate it so that the enterprise product can
  reuse it"
  [user :- UserAttributes]
  (u/prog1 (db/insert! User (merge user {:password (str (UUID/randomUUID))}))
    ;; send an email to everyone including the site admin if that's set
    (email/send-user-joined-admin-notification-email! <>, :google-auth? true)))

(defn fetch-and-update-login-attributes!
  "Update the login attributes for the user at `email`. This call is a no-op if the login attributes are the same"
  [email new-user-attributes]
  (when-let [{:keys [id login_attributes] :as user} (db/select-one User :email email)]
    (if (= login_attributes new-user-attributes)
      user
      (do
        (db/update! User id :login_attributes new-user-attributes)
        (User id)))))
