(ns com.benfle.stripe.books
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [com.benfle.stripe.books.services.prebuilt-checkout :as prebuilt-checkout])
  (:import [com.stripe Stripe]))

(defn set-stripe-api-key!
  []
  (if-let [api-key (not-empty (System/getenv "STRIPE_API_KEY"))]
    (set! Stripe/apiKey api-key)
    (throw (Exception. "The STRIPE_API_KEY environment variable is missing.")))
  :done)

(defn start-http-server
  [{:keys [port handler]}]
  (run-jetty handler
             {:port port
              :join? false}))

(defn stop-http-server
  [http-server]
  (.stop http-server))

(defn -main
  []
  (set-stripe-api-key!)
  (let [port 3000
        prices (inventory/list-prices {:limit 25})
        handler (prebuilt-checkout/web-app {:prices prices
                                            :service-url (str "http://localhost:" port)})]
    (start-http-server {:port port
                        :handler handler})))

(comment

  (require '[clojure.pprint :refer [pprint]]
           '[com.benfle.stripe.books.inventory :as inventory] :reload
           '[com.benfle.stripe.books.services.prebuilt-checkout :as prebuilt-checkout] :reload
           '[com.benfle.stripe.books :as books] :reload)

  (add-tap (fn [v]
             (pprint v)))

  (books/set-stripe-api-key!)

  ;; Start Stripe webhook listener with: stripe listen --forward-to http://localhost:3000/stripe/webhooks
  ;; Replace webhook-secret below

  (let [port 3000
        webhook-secret "whsec_e0b615010c75afe4c664abdb8f7afaf87c3fb99c68308a83651f1af6eab3c4e8"
        handler (prebuilt-checkout/web-app {:service-url (str "http://localhost:" port)
                                            :webhook-secret webhook-secret})]
    (def server (books/start-http-server {:port port
                                          :handler handler})))

  (books/stop-http-server server)

  )
