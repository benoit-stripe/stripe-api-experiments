(ns com.benfle.stripe.setup-intents-api
  "Scenarios for the Setup Intents API."
  (:require [com.benfle.stripe.api :as api]))

(defn confirm-on-create
  [client {:keys [payment-method customer usage]}]
  (api/create-setup-intent client {:confirm true
                                   :customer (:id customer)
                                   :usage usage
                                   :payment_method payment-method}))

(comment

  (require '[com.benfle.stripe.api :as api]
           '[com.benfle.stripe.setup-intents-api :as si] :reload)

  (def client (api/client {:api-key (System/getenv "STRIPE_API_KEY")}))

  (def c (api/create-customer client {}))

  (def si
    (si/confirm-on-create client
                          {:customer c
                           :usage "off_session"
                           :payment-method "pm_card_authenticationRequiredOnSetup"}))

  (pprint si)

  ;; Manual 3DS auth

  (pprint (api/retrieve-setup-intent client si {}))

  (pprint
   (api/list-payment-methods client c {:type "card"}))

  (def c (api/create-customer client {:payment_method "pm_card_authenticationRequiredOnSetup"}))

  (pprint
   (api/list-payment-methods client c {:type "card"}))

  )
