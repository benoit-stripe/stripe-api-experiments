(ns com.benfle.stripe.http
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.time Duration]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers HttpRequest$BodyPublishers]))

(defn client
  "Make a new HTTP client."
  []
  (-> (HttpClient/newBuilder)
      (.build)))

(defn submit
  "Submit an http request and return an http response.

  Request map:
     :scheme                 :http or :https
     :server-name            string
     :server-port            integer
     :uri                    string
     :query-string           string, optional (without leading question mark)
     :request-method         :get/:post/:put/:head/:delete
     :headers                map from downcased string to string
     :body                   InputStream, optional
     :timeout-msec           opt, total request send/receive timeout (default: 10s)

  Response map:
     :status            integer HTTP status code
     :body              InputStream, optional
     :headers           map from downcased string to string"
  [client request]
  (let [{:keys [scheme server-name server-port uri query-string request-method headers body timeout-msec meta]
         :or {timeout-msec 10000}} request
        http-request (-> (HttpRequest/newBuilder)
                         (.uri (URI. (name scheme)
                                     nil
                                     server-name
                                     server-port
                                     uri
                                     query-string
                                     nil))
                         (.method (str/upper-case (name request-method))
                                  (if body
                                    (HttpRequest$BodyPublishers/ofInputStream body)
                                    (HttpRequest$BodyPublishers/noBody)))
                         (.headers (into-array String (mapcat identity headers)))
                         (.timeout (Duration/ofMillis timeout-msec))
                         (.build))
        http-response (.send client
                             http-request
                             (HttpResponse$BodyHandlers/ofInputStream))
        body (.body http-response)]
    (cond-> {:status (.statusCode http-response)
             :headers (->> (.headers http-response)
                           (.map)
                           (map (fn [[key vals]]
                                  ;; XXX: Only return first value if a HTTP header appears more than once
                                  [(str/lower-case key) (first vals)]))
                           (into {}))}
      body (assoc :body body))))
