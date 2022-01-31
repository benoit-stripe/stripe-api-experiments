(ns com.benfle.stripe.books
  "Accepting payment using the prebuilt checkout page."
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clj-http.client :as http]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as html]
            [hiccup.page :refer [html5]])
  (:import [com.stripe Stripe]
           [com.stripe.model Product Price]
           [com.stripe.model.checkout Session]))

(defn set-stripe-api-key!
  []
  (if-let [api-key (not-empty (System/getenv "STRIPE_API_KEY"))]
    (set! Stripe/apiKey api-key)
    (throw (Exception. "The STRIPE_API_KEY environment variable is missing."))))

(defn goodreads-books
  "Import goodreads books from the CSV export."
  []
  (with-open [reader (-> "com/benfle/stripe/books/goodreads_library_export.csv"
                         io/resource
                         io/reader)]
    (->> (csv/read-csv reader)
         rest
         (keep (fn [[id title author _ additional-authors isbn isbn-13]]
                 (when-let [isbn (->> (or isbn-13 isbn)
                                      (re-find #"(\d+)")
                                      second)]
                   {:book/title title
                    :book/isbn isbn
                    :book/image (format "https://covers.openlibrary.org/b/isbn/%s-M.jpg"
                                        isbn)
                    :book/authors (->> (str/split additional-authors #",")
                                       (into [author])
                                       (keep #(not-empty (str/trim %))))
                    ;; random price between $10 and $110
                    :book/price (int (+ 10 (* 100 (rand))))})))
         doall)))

(defn create-product!
  "Create the product and price on Stripe for this book."
  [{:keys [book/title book/isbn book/image book/authors book/price]}]
  (try
    (let [product (Product/create
                   {"name" title
                    "id" isbn
                    "description" (str "By "
                                       (str/join ", " authors))
                    "shippable" true
                    "images" [image]})
          price (Price/create
                 {"currency" "usd"
                  "product" (.getId product)
                  "unit_amount" (* 100 price)})]
      :done)
    (catch Exception ex
      (println (format "Skipping %s: %s"
                       title
                       (.getMessage ex))))))

(defn create-products!
  "Create all the products on Stripe."
  [books]
  (doseq [book books]
    (println "Adding:" (:book/title book))
    (create-product! book)))

(defn list-prices
  "The list of book prices from Stripe."
  [{:keys [limit]}]
  (->> (Price/list
        (cond-> {"expand" ["data.product"]}
          (and (number? limit) (pos? limit)) (assoc "limit" limit)))
       (.getData)
       (into [])
       (map (fn [price]
              (json/read-str (.toJson price)
                             :key-fn keyword)))))

;; Web Server

(defn render-page
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

(defn start-server
  []
  (let [port 3000
        prices (list-prices {:limit 25})]
    (run-jetty (web-app {:prices prices
                         :service-url (str "http://localhost:" port)})
               {:port port
                :join? false})))

(defn stop-server
  [server]
  (.stop server))

(defn -main
  []
  (set-stripe-api-key!)
  (start-server))

(comment

  (require '[clojure.pprint :refer [pprint]]
           '[com.benfle.stripe.books :as books] :reload)

  (books/set-stripe-api-key!)

  ;; To create the products and prices on Stripe
  (def books (books/goodreads-books))
  (books/create-products! books)

  ;; To start/stop the web server
  (def server (books/start-server))
  (books/stop-server server)

  )
