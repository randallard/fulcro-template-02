(ns app.server-components.middleware
  (:require
    [app.application :refer [new-app current-app]]
    [app.client :as client]
    [app.server-components.config :refer [config]]
    [app.server-components.pathom :refer [parser]]
    [app.ui.root :refer [Root]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.algorithms.server-render :as ssr]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.dom-server :as dom]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request wrap-transit-params wrap-transit-response]]
    [hiccup.page :refer [html5]]
    [mount.core :refer [defstate]]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.util.response :as resp]
    [ring.util.response :refer [response file-response resource-response]]
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.fulcro.algorithms.scheduling :as sched]
    [clojure.string :as str]))

(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))

(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (handle-api-request
        (:transit-params request)
        (fn [tx] (parser {:ring/request request} tx)))
      (handler request))))

(defn render-app [path]
  (binding [comp/*app* (new-app)]
    (let [route   (second (str/split path #"/"))
          app     comp/*app*
          _       (client/start app)
          ;; FIXME: If we had a way of detecting an empty tx queue, then these
          ;; arbitrary timeouts would not be necessary
          _       (Thread/sleep 30)
          _       (dr/change-route app [route])
          _       (Thread/sleep 30)
          state   (app/current-state app)
          factory (comp/factory Root)
          query   (comp/get-query Root state)
          props   (fdn/db->tree query state state)]
      [state (dom/render-to-str (factory props))])))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index [route csrf-token]
  (let [[state body] (render-app route)
        script (ssr/initial-state->script-tag state)]
    (html5
      [:html {:lang "en"}
       [:head {:lang "en"}
        [:title "Application"]
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
        [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
                :rel  "stylesheet"}]
        [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
        script
        [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
       [:body
        [:div#app body]
        [:script {:src "js/main/main.js"}]]])))

;; ================================================================================
;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;; but that will not allow full-stack fulcro cards to talk to your server. This
;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;; ================================================================================
(defn wslive [csrf-token]
  (log/debug "Serving wslive.html")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "devcards"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "workspaces/js/main.js"}]]]))

(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri anti-forgery-token session] :as req}]
    (log/spy :info session)
    (let [legal-app-routes #{"/main" "/settings" "/"}]
      (cond
        (contains? legal-app-routes uri)
        (-> (resp/response (index uri anti-forgery-token))
          (resp/content-type "text/html"))

        ;; See note above on the `wslive` function.
        (#{"/wslive.html"} uri)
        (-> (resp/response (wslive anti-forgery-token))
          (resp/content-type "text/html"))

        :else
        (ring-handler req)))))

(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)]
    (-> not-found-handler
      (wrap-api "/api")
      wrap-transit-params
      wrap-transit-response
      (wrap-html-routes)
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-defaults defaults-config)
      wrap-gzip)))



(comment
  (render-app "")

  (def a (new-app))
  (binding [comp/*app* a]
    (client/start a))
  (::app/active-remotes (app/current-state a))
  (dr/change-route a ["settings"])
  (dr/current-route a app.ui.root/TopChrome)
  (-> a ::app/runtime-atom deref ::txn/active-queue)
  (binding [comp/*app* a]
    (let [app     comp/*app*
          state   (app/current-state app)
          factory (comp/factory Root)
          query   (comp/get-query Root state)
          props   (fdn/db->tree query state state)]
      (dom/render-to-str (factory props)))
    )

  )
