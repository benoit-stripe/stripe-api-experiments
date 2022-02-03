(ns com.benfle.stripe.books.services.prebuilt-checkout
  "Web app to sell my books using Stripe's prebuilt checkout page."
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]]
            [com.benfle.stripe.books.inventory :as inventory])
  (:import [com.stripe.model.checkout Session]
           [com.stripe.net Webhook]))

(defn- render-page
  [& contents]
  (html5
   [:head
    [:title "Benoît's Books"]
    [:link {:rel "stylesheet"
            :type "text/css"
            :href "/style.css"}]]
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
                                 :method "POST"}
                          [:input {:type "hidden"
                                   :name "price_id"
                                   :value id}]
                          [:button {:type "submit"}
                           "Buy"]]]])))))))

(defn create-checkout-session!
  [{:keys [service-url price]}]
  (let [session (Session/create
                 {"mode" "payment"
                  "success_url" (str service-url "/payment/success")
                  "cancel_url" (str service-url "/")
                  "line_items" [{"price" price
                                 "quantity" 1}]})]
    {:status 303
     :headers {"Location" (.getUrl session)}}))

(defn render-payment-success
  []
  (render-page
   [:h1 "Thank you for your order."]
   [:p
    [:a {:href "/"}
     "Go back to the list of books."]]))

(defn fulfill-order
  [{:keys [id]}]
  (let [price (-> (Session/retrieve id {"expand" ["line_items"]} nil)
                  (.getLineItems)
                  (.getData)
                  first
                  (.getPrice))]
    (.update price {"active" false})))

(defn handle-webhook-event
  [{:keys [webhook-secret]} req]
  (let [sig-header (get-in req [:headers "stripe-signature"])
        payload (slurp (:body req))
        evt (json/read-str payload :key-fn keyword)]
    ;; verify that the event comes from Stripe
    (try
      (Webhook/constructEvent payload sig-header webhook-secret)
      (case (:type evt)
        "checkout.session.completed" (fulfill-order (-> evt :data :object))
        (tap> {:type :webhook/event
               :webhook.event/type (:type evt)}))
      {:status 200}
      (catch Exception ex
        (tap> {:type :error
               :ex ex})
        {:status 400}))))

(defn web-app
  [{:keys [prices service-url webhook-secret]}]
  (-> (routes
       (GET "/" [] (render-homepage
                    (inventory/list-prices {:active true
                                            :limit 25})))
       (context "/payment" []
         (POST "/checkout" req (create-checkout-session!
                                {:service-url service-url
                                 :price (get-in req [:params "price_id"])}))
         (GET "/success" [] (render-payment-success)))
       (context "/stripe" []
         (POST "/webhooks" req (handle-webhook-event {:webhook-secret webhook-secret} req)))
       (route/not-found "<h1>Page not found</h1>"))
      (wrap-params)
      (wrap-resource "public/com/benfle/stripe/books")
      wrap-content-type))
