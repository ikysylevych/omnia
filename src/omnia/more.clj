(ns omnia.more
  (:require [clojure.core.match :as m]))

(defn take-right [n coll]
  (if-let [x (take-last n coll)] x '()))

(defn bound-inc [value max]
  (let [x (inc value)]
    (if (>= x max) value x)))                               ;; >= because we count from 0

(defn bound-dec [value min]
  (let [x (dec value)]
    (if (< x min) value x)))

(defn foreach [f coll]
  (doall (map f coll)))

(defn zip-all [coll-a coll-b]
  (m/match [coll-a coll-b]
           [[a & t] []] (cons [a nil] (lazy-seq (zip-all t [])))
           [[] [a & t]] (cons [nil a] (lazy-seq (zip-all [] t)))
           [[a & t1] [b & t2]] (cons [a b] (lazy-seq (zip-all t1 t2)))
           :else nil))

(defn reduce-idx
  ([f seed coll]
    (reduce-idx 0 f seed coll))
  ([from f seed coll]
    (if (empty? coll)
      seed
      (recur (inc from) f (f from seed (first coll)) (rest coll)))))
