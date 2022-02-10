(ns app.model.settings
  (:require
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]))

(defmutation save-settings [env {:account/keys      [real-name]
                                 ::file-upload/keys [files]}]
  {}
  (log/info "Saving settings")
  (log/info "New name:" real-name)
  (log/info "New avatar:" (first files)))

(defmutation publish-manifesto [env {::file-upload/keys [files]}]
  {}
  (log/info "Change the world!!!")
  (log/info "Manifesto:" (first files)))

(def resolvers [save-settings publish-manifesto])
