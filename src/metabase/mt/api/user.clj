(ns metabase.mt.api.user
  "Endpoint(s)for setting user attributes."
  (:require [compojure.core :refer [DELETE GET POST PUT]]
            [metabase.api.common :as api]
            [metabase.models.user :refer [User]]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan.db :as db]))

(def ^:private UserAttributes
  (su/with-api-error-message (s/maybe {su/NonBlankString s/Any})
    "value must be a valid user attributes map (name -> value)"))

(api/defendpoint PUT "/:id/attributes"
  "Update the `login_attributes` for a User."
  [id :as {{:keys [login_attributes]} :body}]
  {login_attributes UserAttributes}
  (api/check-404 User id)
  (db/update! User id :login_attributes login_attributes))


(api/define-routes api/+check-superuser)
