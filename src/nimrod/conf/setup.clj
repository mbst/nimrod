(ns nimrod.conf.setup
 (:require
   [clojure.java.io :as io])
 (:use
   [nimrod.core.metric]
   [nimrod.core.store]
   [nimrod.log.tailer])
 (:import [com.typesafe.config ConfigFactory]))

(defn- setup-logs [conf]
  (when-let [logs (.get conf "logs")]
    (doseq [log logs] (start-tailer (key log) (.get (val log) "source") (.get (val log) "interval") (.get (val log) "end")))))

(defn- setup-store [conf]
  (let [store (into {"type" "disk"} (.get conf "store"))
        options (into {} (.get store "options"))
        sampling (into {} (.get store "sampling"))]
    (condp = (store "type")
      "disk" (setup-metric-store (new-disk-store "nimrod-data/db" options sampling))
      (throw (IllegalStateException. (str "Bad store configuration: " (store "type")))))))

(defn setup [source]
  (let [conf (.unwrapped (.root (ConfigFactory/parseFile (io/file source))))]
    (setup-store conf)
    (setup-logs conf)))