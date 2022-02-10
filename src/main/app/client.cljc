(ns app.client
  (:require
    [app.application :refer [current-app]]
    [app.model.session :as session]
    [app.ui.root :as root]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.algorithms.indexing :as indexing]
    [com.fulcrologic.fulcro.rendering.keyframe-render :as kr]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (cssi/upsert-css "componentcss" {:component root/Root})
  (app/mount! (current-app) root/Root "app"))

(defn start [app]
  (app/set-root! app root/Root {:initialize-state? true})
  (dr/initialize! app)
  (uism/begin! app session/session-machine ::session/session
    {:actor/login-form      root/Login
     :actor/current-session root/Session}))

#?(:cljs
   (defn ^:export init []
     (log/info "Application starting.")
     (cssi/upsert-css "componentcss" {:component root/Root})
     (let [app (current-app)]
       (if-let [state (ssr/get-SSR-initial-state)]
         (do
           (reset! (::app/state-atom app) state)
           (app/set-root! app root/Root {:initialize-state? false})
           (app/mount! app root/Root "app" {:initialize-state? false
                                            :hydrate?          true}))
         (do
           (start app)
           (app/mount! app root/Root "app" {:initialize-state? false
                                            :hydrate?          false}))))))
