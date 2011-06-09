(ns nimrod.web.app
 (:use
   [clojure.string :as string :only [split]]
   [clojure.contrib.logging :as log]
   [clojure.contrib.json :as json]
   [compojure.core :as http]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [nimrod.core.metrics]
   [nimrod.log.tailer])
 )

(defonce response-codes {:ok 200 :no-content 204 :not-found 404 :error 500})
(defonce response-headers {"Content-Type" "application/json"})
(defonce cors-response-headers {"Content-Type" "application/json" "Access-Control-Allow-Origin" "*"})

(defn- cors-response 
  ([status body]
    {:headers cors-response-headers :status (response-codes status) :body (json/json-str body)}
    )
  ([status]
    {:headers cors-response-headers :status (response-codes status)}
    )
  )

(defn- response 
  ([status body]
    {:headers response-headers :status (response-codes status) :body (json/json-str body)}
    )
  ([status]
    {:headers response-headers :status (response-codes status)}
    )
  )

(defn- wrap-errors [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception ex
        (log/error (.getMessage ex) ex)
        (response :error {:error (.getMessage ex)}))
      )
    )
  )

(http/defroutes nimrod-routes
  (http/POST "/logs" [file interval]
    (let [tailer (start-tailer file (Long/parseLong interval))]
      (response :ok {tailer file})
      )
    )
  (http/GET "/logs" []
    (cors-response :ok (list-tailers))
    )
  (http/DELETE "/logs/:log-id" [log-id]
    (stop-tailer log-id)
    (response :no-content)
    )
  (http/GET "/logs/:log-id/:metric-type" [log-id metric-type]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (list-metrics metric log-id)]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-type)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/:metric-id" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (read-metric metric log-id metric-id)]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (http/POST ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+"] [log-id metric-type metric-id limit]
    (if-let [metric (metric-types (keyword metric-type))]
      (do 
        (reset-history metric log-id metric-id (Long/parseLong limit))
        (response :no-content)
        )
      (response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history" :metric-id #"[^/?#]+"] [log-id metric-type metric-id]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (read-history metric log-id metric-id nil)]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (http/GET ["/logs/:log-id/:metric-type/:metric-id/history/:tags" :metric-id #"[^/?#]+" :tags #"[^/?#]+"] [log-id metric-type metric-id tags]
    (if-let [metric (metric-types (keyword metric-type))]
      (if-let [result (read-history metric log-id metric-id (into #{} (string/split tags #",")))]
        (cors-response :ok result)
        (cors-response :not-found)
        )
      (cors-response :error {:error (str "Bad metric type: " metric-id)})
      )
    )
  (route/not-found "")
  )

(defonce nimrod-app
  (handler/api (wrap-errors nimrod-routes))
  )