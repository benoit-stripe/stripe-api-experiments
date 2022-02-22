(ns com.benfle.stripe.api
  "Stripe API client."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [com.benfle.stripe.http :as http]))

(defn client
  [{:keys [api-key]}]
  (let [http-client (http/client)]
    {:http-client http-client
     :api-key api-key
     :base-http-request {:scheme :https
                         :server-name "api.stripe.com"
                         :server-port 443
                         :headers {"Authorization" (format "Bearer %s" api-key)}}}))

(defn- map->query-string
  [m]
  (let [flatten-map (fn f [m]
                      (when (not-empty m)
                        (mapcat (fn [[k v]]
                                  (if (map? v)
                                    (map #(into [k] %) (f v))
                                    [[k v]]))
                                m)))]
    (when (not-empty m)
      (->> m
           flatten-map
           (map (fn [field]
                  (let [path (->> field
                                  rest
                                  butlast
                                  (map #(str "[" (name %) "]"))
                                  (into [(name (first field))])
                                  (str/join ""))
                        value (last field)]
                    (if (coll? value)
                      (->> value
                           (map #(str path "[]=" (str %)))
                           (str/join "&"))
                      (str path "=" (str value))))))
           (str/join "&")))))

(defn- submit
  "Helper for the API operations."
  [{:keys [http-client base-http-request]} request]
  (let [http-request (merge base-http-request request)
        http-response (http/submit http-client http-request)
        payload (slurp (:body http-response))]
    (if (= 200 (:status http-response))
      (json/read-str payload :key-fn keyword)
      (throw (ex-info "API Error"
                      {:http-request http-request
                       :http-response (assoc http-response :body payload)})))))

(defn create-customer
  [client params]
  (submit client {:request-method :post
                  :uri "/v1/customers"
                  :query-string (map->query-string params)}))

(defn list-payment-methods
  [client {:keys [id]} params]
  (submit client {:request-method :get
                  :uri (str "/v1/customers/" id "/payment_methods")
                  :query-string (map->query-string params)}))

(defn create-payment-intent
  [client params]
  (submit client {:request-method :post
                  :uri "/v1/payment_intents"
                  :query-string (map->query-string params)}))

(defn update-payment-intent
  [client {:keys [id]} params]
  (submit client {:request-method :post
                  :uri (str "/v1/payment_intents/" id)
                  :query-string (map->query-string params)}))

(defn retrieve-payment-intent
  [client {:keys [id]} params]
  (submit client {:request-method :get
                  :uri (str "/v1/payment_intents/" id)
                  :query-string (map->query-string params)}))

(defn confirm-payment-intent
  [client {:keys [id]} params]
  (submit client {:request-method :post
                  :uri (str "/v1/payment_intents/" id "/confirm")
                  :query-string (map->query-string params)}))

(defn capture-payment-intent
  [client {:keys [id]} params]
  (submit client {:request-method :post
                  :uri (str "/v1/payment_intents/" id "/capture")
                  :query-string (map->query-string params)}))

(defn create-setup-intent
  [client params]
  (submit client {:request-method :post
                  :uri "/v1/setup_intents"
                  :query-string (map->query-string params)}))

(defn update-setup-intent
  [client {:keys [id]} params]
  (submit client {:request-method :post
                  :uri (str "/v1/setup_intents/" id)
                  :query-string (map->query-string params)}))

(defn retrieve-setup-intent
  [client {:keys [id]} params]
  (submit client {:request-method :get
                  :uri (str "/v1/setup_intents/" id)
                  :query-string (map->query-string params)}))

(defn confirm-setup-intent
  [client {:keys [id]} params]
  (submit client {:request-method :get
                  :uri (str "/v1/setup_intents/" id "/confirm")
                  :query-string (map->query-string params)}))

(defn cancel-setup-intent
  [client {:keys [id]} params]
  (submit client {:request-method :get
                  :uri (str "/v1/setup_intents/" id "/cancel")
                  :query-string (map->query-string params)}))

(comment

  (require '[clojure.pprint :refer [pprint]]
           '[com.benfle.stripe.http] :reload
           '[com.benfle.stripe.api :as api] :reload)

  (def client (api/client {:api-key (System/getenv "STRIPE_API_KEY")}))

  (def response (api/create-payment-intent client {:amount 100
                                                   :currency "usd"}))


  )
