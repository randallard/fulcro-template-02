(ns app.application
  (:require
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.tx-processing :as txn]
    #?@(:cljs [[com.fulcrologic.fulcro.networking.http-remote :as net]]
        :clj  [[edn-query-language.core :as eql]
               [mount.core :refer [defstate]]
               [app.server-components.pathom :as parser]])
    [taoensso.timbre :as log]))

#?(:cljs (def secured-request-middleware
           ;; The CSRF token is embedded via server_components/html.clj
           (->
             (net/wrap-csrf-token (or js/fulcro_network_csrf_token "TOKEN-NOT-IN-HTML!"))
             (net/wrap-fulcro-request))))

#?(:cljs
   (defonce SPA (app/fulcro-app
                  {;; This ensures your client can talk to a CSRF-protected server.
                   ;; See middleware.clj to see how the token is embedded into the HTML
                   :remotes {:remote (net/fulcro-http-remote
                                       {:url                "/api"
                                        :request-middleware secured-request-middleware})}})))

#?(:clj
   (defstate ssr-remote
     :start
     (let [parser parser/parser]
       {:transmit! (fn transmit! [_ {:keys [::txn/ast ::txn/result-handler ::txn/update-handler]}]
                     (let [edn           (eql/ast->query ast)
                           ok-handler    (fn [result]
                                           (try
                                             (result-handler result)
                                             (catch Exception e
                                               (log/error e "Result handler failed with an exception."))))
                           error-handler (fn [error-result]
                                           (try
                                             (result-handler (merge error-result {:status-code 500}))
                                             (catch Exception e
                                               (log/error e "Error handler failed with an exception."))))]
                       (try
                         (let [result (parser {} edn)]
                           (log/info "Parsed " edn result)
                           (ok-handler {:transaction edn
                                        :body        result
                                        :status-code 200}))
                         (catch Exception e
                           (log/error e "Cannot processed request SSR Remote: " edn)
                           (error-handler {:transaction edn
                                           :body        {:mutation-error "Processing threw an exception"}
                                           :status-code 500})))))})))

(defn current-app []
  #?(:clj  comp/*app*
     :cljs SPA))


#?(:clj
   (defn new-app []
     (app/fulcro-app {:remotes {:remote ssr-remote}})))
