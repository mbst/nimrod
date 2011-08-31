(ns nimrod.web.app
 (:use
   [clojure.string :as string :only [split]]
   [clojure.contrib.logging :as log]
   [clojure.contrib.json :as json]
   [compojure.core :as http]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [ring.util.response :as response]
   [nimrod.core.metrics]
   [nimrod.log.tailer])
 )

(defonce response-codes {:ok 200 :no-content 204 :not-found 404 :error 500})
(defonce std-response-headers {"Content-Type" "application/json"})
(defonce cors-response-headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*"})

(defn- extract-tags [tags]
  (when (seq tags) (into #{} (string/split tags #",")))
  )

(defn- drop-last-char [s]
  (apply str (drop-last (seq s)))
  )

(defn- std-response
  ([status]
    (std-response status std-response-headers nil)
    )
  ([status body]
    (std-response status std-response-headers body)
    )
  ([status headers body]
    {:headers headers :status (response-codes status) :body (if body (json/json-str body) nil)}
    )
  )

(defn- cors-response 
  ([status]
    (std-response status cors-response-headers nil)
    )
  ([status body]
    (std-response status cors-response-headers body)
    )
  )

(defn- redirect-response [url]
    (response/redirect url)
  )

(defn- wrap-errors [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception ex
        (log/error (.getMessage ex) ex)
        (std-response :error {:error (.getMessage ex)}))
      )
    )
  )

(http/defroutes nimrod-routes

  (http/POST "/logs" [file interval]
    (let [tailer (start-tailer file (Long/parseLong (or interval "1000")))]
      (std-response :ok {tailer file})
      )
    )
  (http/GET "/logs" []
    (cors-response :ok (list-tailers))
    )
  (http/GET "/logs/" [:as request]
    (redirect-response (drop-last-char (request :uri)))
    )
  (http/DELETE "/logs/:log-id" [log-id]
    (stop-tailer log-id)
    (std-response :no-content)
    )

  (http/GET ["/logs/:log-id/:metric-type" :tags #"[^/?#]+"] [log-id metric-type tags]
    (if-let [metrics (metrics (keyword metric-type))]
      (if-let [result (list-metrics metrics log-id (extract-tags tags))]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/"] [log-id metric-type :as request]
    (redirect-response (drop-last-char (request :uri)))
    )
  (http/DELETE ["/logs/:log-id/:metric-type" :age #"\d+" :tags #"[^/?#]+"] [log-id metric-type age tags]
    (if-let [metrics (metrics (keyword metric-type))]
      (do
        (if (not (nil? age))
          (expire-metrics metrics log-id (Long/parseLong age))
          (remove-metrics metrics log-id (extract-tags tags))
          )
        (std-response :no-content)
        )
      (std-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (http/GET ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metrics (metrics (keyword metric-type))]
      (if-let [result (read-metric metrics log-id metric-id)]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/DELETE ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metrics (metrics (keyword metric-type))]
      (do
        (remove-metric metrics log-id metric-id)
        (std-response :no-content)
        )
      (std-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (http/POST ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+"] [log-id metric-type metric-id limit]
    (if-let [metrics (metrics (keyword metric-type))]
      (do 
        (reset-history metrics log-id metric-id (Long/parseLong limit))
        (std-response :no-content)
        )
      (std-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+" :tags #"[^/?#]+"] [log-id metric-type metric-id tags]
    (if-let [metrics (metrics (keyword metric-type))]
      (if-let [result (read-history metrics log-id metric-id (extract-tags tags))]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )

  (route/not-found "")

  )

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes))
  )