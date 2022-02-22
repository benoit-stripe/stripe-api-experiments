(ns com.benfle.stripe.payment-intents-api
  "Scenarios for the Payment Intents API."
  (:require [com.benfle.stripe.api :as api]))

(defn confirm-on-create
  "PI is confirmed when created."
  [client {:keys [amount currency payment-method-data]}]
  (api/create-payment-intent client {:amount amount
                                     :currency currency
                                     :payment_method_data payment-method-data
                                     :confirm true}))

(defn create-then-confirm
  "PI is first created, then confirmed."
  [client {:keys [amount currency payment-method]}]
  (let [pi (api/create-payment-intent client {:amount amount
                                              :currency currency})
        pi (api/update-payment-intent client pi {:payment_method payment-method})
        pi (api/confirm-payment-intent client pi {})]
    ;; TODO: authenticate w/ 3DS programmatically
    (api/retrieve-payment-intent client pi {})))

(defn create-payment-with-manual-capture
  [client {:keys [amount currency payment-method]}]
  (let [pi (api/create-payment-intent client {:amount amount
                                              :currency currency
                                              :capture_method "manual"
                                              :payment_method payment-method
                                              :confirm true})]
    ;; TODO: authenticate w/ 3DS programmatically
    (api/capture-payment-intent client pi {})))

(comment

  (require '[com.benfle.stripe.api :as api]
           '[com.benfle.stripe.payment-intents-api :as pi-api] :reload)

  (def client (api/client {:api-key (System/getenv "STRIPE_API_KEY")}))

  (def pi
    (pi-api/create-payment-with-manual-capture client
                                               {:amount 2500
                                                :currency "usd"
                                                :payment-method "pm_card_authenticationRequiredOnSetup"}))

  (def pi (api/retrieve-payment-intent client pi {}))
  (def pi (api/capture-payment-intent client pi {}))

  )
