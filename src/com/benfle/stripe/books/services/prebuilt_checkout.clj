(ns com.benfle.stripe.books.services.prebuilt-checkout
  "Web app to sell my books using Stripe's prebuilt checkout page."
  (:require [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]])
  (:import [com.stripe.model.checkout Session]))

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

(defn web-app
  [{:keys [prices service-url]}]
  (-> (routes
       (GET "/" [] (render-homepage prices))
       (context "/payment" []
         (POST "/checkout" req (create-checkout-session!
                                {:service-url service-url
                                 :price (get-in req [:params "price_id"])}))
         (GET "/success" [] (render-payment-success)))
       (route/not-found "<h1>Page not found</h1>"))
      (wrap-params)
      (wrap-resource "public/com/benfle/stripe/books")
      wrap-content-type))
