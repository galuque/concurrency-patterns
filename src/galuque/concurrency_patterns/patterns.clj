(ns galuque.concurrency-patterns.patterns
  (:require [clojure.core.async :as async :refer [go go-loop chan <!! >!! <! >! alts! alt!! alt! timeout]]))

;; This pattern consist of a function that returns a channel
;; Channels are values

(defn boring [msg]
  (let [c (chan)]
    (go-loop [i 0]
      (>! c (str msg " " i))
      (Thread/sleep (* (Math/random) 1e3))
      (recur (inc i)))
    c))

(defn main []
  (let [c (boring "boring!")]
    (dotimes [_ 5]
      (println "You say:" (<!! c)))
    (println "You're boring. I'm leaving.")))

(comment

  (main)
)

(defn main-2 []
  (let [joe (boring "Joe")
        ann (boring "Ann")]
    (dotimes [_ 5]
      (println (<!! joe))
      (println (<!! ann)))
    (println "You're both boring. I'm leaving.")))

(comment

  (main-2)
)


;; Multiplexing
;; These programs make Joe and Ann count in lockstep
;; With a fan-in function we can let whoever is ready talk

(defn fan-in [<ch1 <ch2]
  (let [c (chan)]
    (go-loop [] (>! c (<! <ch1)) (recur))
    (go-loop [] (>! c (<! <ch2)) (recur))
    c))


(defn main-3 []
  (let [c (fan-in (boring "Joe") (boring "Ann"))]
    (dotimes [_ 10]
      (println (<!! c)))
    (println "You're both boring. I'm leaving.")))

(comment

  (main-3)
)


;; Restoring sequence
;; Channels are values, we can pass them around
;; Wait channel idiom

(defn boring-2 [msg]
  (let [c           (chan)
        wait-for-it (chan)]
    (go-loop [i 0]
      (>! c {:str (str msg " " i) :wait wait-for-it}) ;; sends the channel too
      (Thread/sleep (* (Math/random) 1e3))
      (<!! wait-for-it) ;; Blocks until a message is received from main
      (recur (inc i)))
    c))

(defn main-4 []
  (let [c (fan-in (boring-2 "Joe") (boring-2 "Ann"))]
    (dotimes [_ 5]
      (let [msg1 (<!! c)
            msg2 (<!! c)]
        (println (:str msg1))
        (println (:str msg2))
        (>!! (:wait msg1) true)
        (>!! (:wait msg2) true)))
    (println "You're both boring. I'm leaving.")))

(comment

  (main-4)
)



;; Go's select is async/alts!
;; Control structure unique to concurrency
;; Another way to handle multiple channels
;; like a switch but each case is a communication


(defn fan-in-2 [<ch1 <ch2]
  (let [c (chan)]
    (go-loop []
      (let [[v _] (alts! [<ch1 <ch2])]
        (>!! c v))
      (recur))
    c))


(defn main-5 []
  (let [c (fan-in-2 (boring "Joe") (boring "Ann"))]
    (dotimes [_ 10]
      (println (<!! c)))
    (println "You're both boring. I'm leaving.")))

(comment

  (main-5)
)


;; Timeout using alts!
;; If one message take longer than 800 msecsit tiimeouts
(defn main-6 []
  (let [c (boring "Joe")]
    (loop []
      (alt!!
        c ([val] (println val) (recur))
        (timeout 800) (println "You're to slow")))))

(comment

  (main-6)
)

;; If the whole loop takes longer than 5 sec it timeouts
(defn main-7 []
  (let [c (boring "Joe")
        t (timeout 5000)]
    (loop []
      (alt!!
        c ([val] (println val) (recur))
        t (println "You're to slow")))))

(comment

  (main-7)
)

;; Signaling to quit
(defn boring-3 [msg <quit]
  (let [c (chan)]
    (go-loop [i 0]
      (alt!
        [[c (str msg " " i)]] (do (Thread/sleep (* (Math/random) 1e3))
                                  (recur (inc i)))
        <quit ([_] :return)))
    c))

(defn main-8 []
  (let [<quit (chan)
        c     (boring-3 "Joe" <quit)
        times (Math/ceil (* (Math/random) 10))]
    (dotimes [_ times]
      (println (<!! c)))
    (>!! <quit true)))

(comment

  (main-8)
  
)

;; Handle graceful shutdown
;; round-trip communication
(defn boring-4 [msg <quit]
  (let [c (chan)]
    (go-loop [i 0]
      (let [msg' (str msg " " i)]
        (alt!
          [[c msg']] (do (Thread/sleep (* (Math/random) 1e3))
                         (recur (inc i)))
          <quit      ([_]
                      (println "Cleaning up!")
                      (>! <quit "See you!")
                      :return))))
    c))

(defn main-9 []
  (let [<quit (chan)
        c     (boring-4 "Joe" <quit)
        times (Math/ceil (* (Math/random) 10))]
    (dotimes [_ times]
      (println (<!! c)))
    (>!! <quit "Bye!")
    (println "Joe says:" (<!! <quit))))

(comment
  
  (main-9)

  )

;; Daisy-chain

(defn f [left right]
  (go (>! left (inc (<! right)))))

;; Could this be made faster?
(defn main-10 []
  (let [n         100000
        leftmost  (chan)
        channels  (repeatedly n chan)
        rightmost (reduce (fn [left right]
                            (f left right)
                            right)
                          leftmost
                          channels)]
    (go (>! rightmost 1))
    (println (<!! leftmost))))

;; David Nolen's version (around 3x faster)
(defn main-10-nolen []
  (let [leftmost (chan)
        rightmost (loop [n 100000 left leftmost]
                    (if-not (pos? n)
                      left
                      (let [right (chan)]
                        (f left right)
                        (recur (dec n) right))))]
    (go
      (>! rightmost 1)
      (println (<! leftmost)))))

(comment
  
  (time
   (main-10))
  
  (time
   (main-10-nolen))
  
  )