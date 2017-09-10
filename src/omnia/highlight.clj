(ns omnia.highlight
  (require [clojure.core.match :as m]
           [clojure.string :as s]))

(defrecord Transiton [state guard nodes valid?])

(declare transitions)

(def ^:const -list :list)
(def ^:const -vector :vector)
(def ^:const -map :map)
(def ^:const -char :char)
(def ^:const -number :number)
(def ^:const -string :string)
(def ^:const -string* :string*)
(def ^:const -keyword :keyword)
(def ^:const -function :function)
(def ^:const -comment :comment)
(def ^:const -word :word)
(def ^:const -text :text)
(def ^:const -break :break)
(def ^:const -space :space)
(def ^:const -select :selection)
(def ^:const -back :background)

(def ^:const not-nodes :not-nodes)

(def ^:const empty-vec [])

(def ^:const numbers #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9})

(def ^:const words #{[\n \i \l]
                     [\t \r \u \e]
                     [\f \a \l \s \e]})

(defn- invert [nodes]
  (let [ts (delay (->> nodes
                       (mapcat transitions)
                       (mapv :guard)))]
    #(reduce
       (fn [bool p] (and bool (not (p %)))) true @ts)))

(defn transiton [{:keys [state
                         guard
                         nodes
                         valid?]
                  :or   {valid? (comp not empty?)
                         nodes  []}
                  :as   transiton}]
  (assert (not (nil? state)) "A transiton must always have a state")
  (assert (not (nil? guard)) "A transiton must always have a guard")
  (let [nguard (if (= not-nodes guard) (invert nodes) guard)]
    (Transiton. state nguard nodes valid?)))

(def ->break
  (transiton {:state -break
              :guard #(= \newline %)
              :nodes [-space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->space
  (transiton {:state -space
              :guard #(= \space %)
              :nodes [-break
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->open-list
  (transiton {:state -list
              :guard #(= \( %)
              :nodes [-break
                      -space
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -function]}))

(def ->close-list
  (transiton {:state -list
              :guard #(= \) %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->open-vector
  (transiton {:state -vector
              :guard #(= \[ %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->close-vector
  (transiton {:state -vector
              :guard #(= \] %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->open-map
  (transiton {:state -map
              :guard #(= \{ %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->close-map
  (transiton {:state -map
              :guard #(= \} %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->open-string
  (transiton {:state -string
              :guard #(= \" %)
              :nodes [-string*]}))

(def ->close-string
  (transiton {:state -string*
              :guard #(= \" %)
              :nodes [-break
                      -space
                      -word
                      -list
                      -vector
                      -map
                      -number
                      -char
                      -string
                      -comment
                      -keyword
                      -text]}))

(def ->comment
  (transiton {:state -comment
              :guard #(= \; %)
              :nodes [-break]}))

(def ->char
  (transiton {:state -char
              :guard #(= \\ %)
              :nodes [-break
                      -space]}))

(def ->number
  (transiton {:state -number
              :guard #(contains? numbers %)
              :nodes [-break
                      -space
                      -list
                      -vector
                      -map
                      -string
                      -comment]}))

(def ->keyword
  (transiton {:state -keyword
              :guard #(= \: %)
              :nodes [-list
                      -vector
                      -map
                      -string
                      -char
                      -comment
                      -break
                      -space]}))

(def ->word
  (transiton {:state  -word
              :guard  #(some (fn [[l & _]] (= l %)) words)
              :nodes  [-break
                       -space
                       -list
                       -vector
                       -map
                       -string
                       -char
                       -comment]
              :valid? #(some (fn [w] (= % w)) words)}))

(def ->function
  (transiton {:state -function
              :guard not-nodes
              :nodes [-break
                      -space
                      -list
                      -vector
                      -map
                      -comment
                      -char
                      -string]}))

(def ->text
  (transiton {:state -text
              :guard not-nodes
              :nodes [-break
                      -space
                      -list
                      -vector
                      -map
                      -char
                      -string
                      -comment]}))

(def transitions
  {-list     [->open-list ->close-list]
   -vector   [->open-vector ->close-vector]
   -map      [->open-map ->close-map]
   -function [->function]
   -text     [->text]
   -string   [->open-string]
   -string*  [->close-string]
   -comment  [->comment]
   -word     [->word]
   -number   [->number]
   -char     [->char]
   -keyword  [->keyword]
   -break    [->break]
   -space    [->space]})

(defn transition [transiton c]
  (or (some->> (:nodes transiton)
               (map transitions)
               (flatten)
               (some #(when ((:guard %) c) %)))
      transiton))

(defn changed? [this that]
  (not= (:state this) (:state that)))

(defn emit [transiton pushed f]
  (if ((:valid? transiton) pushed)
    (f pushed (:state transiton))
    (f pushed (:state ->text))))

;; If -text sits higher in the node list than -word, words will be processed as text
(defn process [stream f]
  (loop [rem       stream
         transiton ->break
         store     empty-vec
         ems       empty-vec]
    (m/match [store rem]
             [[] []] ems
             [_ []] (->> (emit transiton store f)
                         (conj ems)
                         (recur rem transiton empty-vec))
             [_ [a & tail]]
             (let [t (transition transiton a)]
               (if (changed? transiton t)
                 (->> (emit transiton store f)
                      (conj ems)
                      (recur tail t [a]))
                 (recur tail t (conj store a) ems))))))