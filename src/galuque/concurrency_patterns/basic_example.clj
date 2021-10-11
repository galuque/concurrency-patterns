(ns galuque.concurrency-patterns.basic-example
  (:require [clojure.core.async :as async :refer [<!! >! chan go]]))

(defn boring [msg c]
  (go
    (doseq [i (range)]
      (>! c (str msg " " i))
      (Thread/sleep (* (Math/random) 1e3)))))

(defn main []
  (let [c (chan)]
    (boring "boring!" c)
    (doseq [_ (range 5)]
      (println "You say:" (<!! c)))
    (println "You're boring. I'm leaving.")))

(comment
  (main)
  )