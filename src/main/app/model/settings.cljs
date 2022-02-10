(ns app.model.settings
  (:require
    [com.fulcrologic.fulcro.networking.http-remote :as http-remote]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defmutation save-settings [_]
  (action [{:keys [app]}]
    (df/set-load-marker! app ::save :loading))
  (progress-action [{:keys [state] :as env}]
    (swap! state assoc-in [:component/id :settings :ui/progress] (http-remote/overall-progress env)))
  (ok-action [{:keys [app]}]
    (df/remove-load-marker! app ::save))
  (error-action [{:keys [app]}]
    (df/set-load-marker! app ::save :failed))
  (remote [_] true))

(defmutation publish-manifesto [_]
  (remote [_] true))
