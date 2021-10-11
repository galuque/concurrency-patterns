(ns galuque.concurrency-patterns.basic-example
  (:require [clojure.core.async :as async :refer [go chan >! <!!]]))

(defn boring [msg c]
  (go
    (loop [i 0]
      (>! c (str msg " " i))
      (Thread/sleep (* (Math/random) 1e3))
      (recur (inc i)))))

(defn main []
  (let [c (chan)]
    (boring "boring!" c)
    (loop [i 0]
      (when (< i 5)
        (println "You say:" (<!! c))
        (recur (inc i))))
    (println "You're boring. I'm leaving.")))

(comment
  (main)
  )