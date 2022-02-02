(ns com.benfle.stripe.books.inventory
  "Functions to manage the inventory of books."
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [com.stripe.model Product Price]))

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
                   #:book{:title title
                          :isbn isbn
                          :image (format "https://covers.openlibrary.org/b/isbn/%s-M.jpg"
                                         isbn)
                          :authors (->> (str/split additional-authors #",")
                                        (into [author])
                                        (keep #(not-empty (str/trim %))))
                          ;; random price between $10 and $110
                          :price (int (+ 10 (* 100 (rand))))})))
         doall)))

(defn create-product-and-price!
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

(defn create-products-and-prices!
  "Create all the products and prices on Stripe."
  [books]
  (doseq [book books]
    (println "Adding:" (:book/title book))
    (create-product-and-price! book)))

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
