(ns com.benfle.stripe.payment-intents-api
  "Scenarios for the Payment Intents API."
  (:require [com.benfle.stripe.api :as api]))

;; source: https://stripe.com/docs/testing#regulatory-cards
(def credit-card-numbers
  #{{:succeed? true
     :authentication-required :never
     :number "4242424242424242"}
    {:succeed? true
     :authenticated-required :on-setup
     :number "4000002500003155"}
    {:succeed? true
     :authentication-required :always
     :number "4000002760003184"}
    {:succeed? false
     :authenticated-required :on-setup
     :number "4000008260003178"}
    ;; TODO: Clarify scenario
    {:succeed? true
     :authenticated-required :always
     :number "4000008260003178"}})

(defn card-such-that
  "A card that matches the given predicate."
  [p]
  (when-let [{:keys [number]} (->> credit-card-numbers
                                   (filter p)
                                   rand-nth)]
    {:exp_month "01"
     :exp_year "2032"
     :number number
     :cvc "000"}))

;; source: https://stripe.com/docs/testing#regulatory-cards
(def payment-methods
  #{{:succeed? true
     :authentication-required :always
     :id "pm_card_authenticationRequired"}
    {:succeed? true
     :authentication-required :on-setup
     :id "pm_card_authenticationRequiredOnSetup"}})

(defn pm-such-that
  "A payment method that matches the predicate."
  [p]
  (->> payment-methods
       (filter p)
       rand-nth
       :id))

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

  (require '[com.benfle.stripe.api :as api] :reload
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
