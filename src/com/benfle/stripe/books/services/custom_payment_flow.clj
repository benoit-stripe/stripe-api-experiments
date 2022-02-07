(ns com.benfle.stripe.books.services.custom-payment-flow
  (:require [clojure.data.json :as json]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]]
            [com.benfle.stripe.books.inventory :as inventory])
  (:import [com.stripe.model PaymentIntent Price]
           [com.stripe.param PaymentIntentCreateParams PaymentIntentCreateParams$AutomaticPaymentMethods]
           [com.stripe.net Webhook]))

(defn- render-page
  [& contents]
  (html5
   [:head
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:title "Benoît's Books"]
    [:link {:rel "stylesheet"
            :type "text/css"
            :href "/style.css"}]
    [:script {:src "https://js.stripe.com/v3/"}]
    [:script {:src "/checkout.js"}]]
   (into [:body]
         contents)))

(defn render-homepage
  [prices]
  (render-page
   [:h1 "Benoît's Books"]
   (into [:div#products]
         (->> prices
              (map (fn [{:keys [unit_amount_decimal product id]}]
                     (let [{:keys [name description images]} product]
                       [:div.product
                        [:img.product_image {:src (first images)}]
                        [:div.product_details
                         [:div.product_name name]
                         [:div.product_description description]
                         [:div.product_price (str "$" (/ unit_amount_decimal 100.0))]]
                        [:div.product_checkout
                         [:form {:action "/payment/checkout"
                                 :method "GET"}
                          [:input {:type "hidden"
                                   :name "price_id"
                                   :value id}]
                          [:input {:type "hidden"
                                   :name "amount"
                                   :value unit_amount_decimal}]
                          [:button {:type "submit"}
                           "Buy"]]]])))))))

(defn create-payment-form!
  [{:keys [service-url]} {:keys [price amount]}]
  (let [params (-> (PaymentIntentCreateParams/builder)
                   (.setAmount (Long/parseLong amount))
                   (.setCurrency "usd")
                   (.putAllMetadata {"price" price})
                   (.setAutomaticPaymentMethods
                    (-> (PaymentIntentCreateParams$AutomaticPaymentMethods/builder)
                        (.setEnabled true)
                        (.build)))
                   (.build))
        payment-intent (PaymentIntent/create params)
        client-secret (.getClientSecret payment-intent)
        return-url (str service-url "/payment/status")]
    (render-page
     [:form#payment-form
      [:div#payment-element]
      [:button#submit
       "Submit"]
      [:div#error-message]]
     [:script {:type "text/javascript"}
      (format "setupPayment('%s', '%s');" client-secret return-url)])))

(defn render-payment-status
  []
  (render-page
   [:div#message]
   [:script {:type "text/javascript"}
    "handlePaymentStatus();"]
   [:p
    [:a {:href "/"}
     "Go back to the list of books."]]))

;; Note: There is a risk of "double sale" if another customer purchases
;; the same product before we have time to deactivate the price.
;; It is still unclear whether this issue can be solved with Stripe or
;; if we have to implement our own lock.
(defn fulfill-order
  [{:keys [metadata]}]
  (let [{:keys [price]} metadata]
    (.update (Price/retrieve price) {"active" false})))

(defn handle-webhook-event
  [{:keys [webhook-secret]} req]
  (let [sig-header (get-in req [:headers "stripe-signature"])
        payload (slurp (:body req))
        evt (json/read-str payload :key-fn keyword)]
    ;; verify that the event comes from Stripe
    (try
      (Webhook/constructEvent payload sig-header webhook-secret)
      (case (:type evt)
        "payment_intent.succeeded" (fulfill-order (-> evt :data :object))
        (tap> {:type :webhook/event
               :webhook.event/type (:type evt)}))
      {:status 200}
      (catch Exception ex
        (tap> {:type :error
               :ex ex})
        {:status 400}))))

(defn web-app
  [{:keys [service-url webhook-secret] :as config}]
  (-> (routes
       (GET "/" [] (render-homepage
                    (inventory/list-prices {:active true
                                            :limit 25})))
       (context "/payment" []
         (GET "/checkout" req (create-payment-form!
                               config
                               {:price (get-in req [:params "price_id"])
                                :amount (get-in req [:params "amount"])}))
         (GET "/status" [] (render-payment-status)))
       (context "/stripe" []
         (POST "/webhooks" req (handle-webhook-event {:webhook-secret webhook-secret} req)))
       (route/not-found "<h1>Page not found</h1>"))
      (wrap-params)
      (wrap-resource "public/com/benfle/stripe/books")
      wrap-content-type))
