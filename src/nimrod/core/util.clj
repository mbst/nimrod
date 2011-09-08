(ns nimrod.core.util
 (:use 
   [clojure.contrib.logging :as log]
   [clojure.contrib.singleton :as singleton])
 (:import [java.text SimpleDateFormat] [java.util Date] [java.util.concurrent TimeUnit])
 )

(def get-simple-date-format (singleton/per-thread-singleton #(SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssz")))

(defn new-agent [state]
  (let [a (agent state)]
    (set-error-handler! a #(log/log :error (.getMessage %2) %2))
    (set-error-mode! a :continue)
    a
    )
  )

(defn date-to-string [t]
  (.format (get-simple-date-format) (Date. t))
  )

(defn string-to-date [d]
  (.getTime (.parse (get-simple-date-format) d))
  )

(defn seconds [t] (.toMillis (TimeUnit/SECONDS) t))

(defn minutes [t] (.toMillis (TimeUnit/MINUTES) t))

(defn hours [t] (.toMillis (TimeUnit/HOURS) t))

(defn days [t] (.toMillis (TimeUnit/DAYS) t))

(defn unrationalize [n] (if (ratio? n) (float n) n))

(defn display [m] (reduce conj (sorted-map) (map #(vector %1 (unrationalize %2)) (keys m) (vals m))))