(ns omnia.input
  (:gen-class)
  (require [clojure.core.match :as m]))

(defrecord Seeker [lines cursor])

(def empty-seeker (Seeker. [] [0 0]))

(def matching-rules {\{ \}
                     \[ \]
                     \( \)
                     \" \"})

(defn line
  ([seeker]
   (line seeker (:cursor seeker)))
  ([seeker [x y]]
   (-> seeker :lines (nth y []))))

(defn sym-at [seeker]
  (let [[x y] (:cursor seeker)]
    (-> seeker line (nth x nil))))

(defn peer [seeker f]
  (let [[_ y] (:cursor seeker)]
    (update-in seeker [:lines]
               #(->> % (split-at y) (map vec) (apply f) (map vec) vec))))

(defn slice [seeker f]
  (let [[x _] (:cursor seeker)]
    (peer seeker (fn [l [line & r]]
                   (let [nl (->> line (split-at x) (map vec) (apply f))]
                     (concat (conj l nl) r))))))

(defn slicel [seeker f]
  (slice seeker (fn [l r] (concat (f l) r))))

(defn slicer [seeker f]
  (slice seeker (fn [l r] (concat l (f r)))))

(defn move [seeker f]
  (update-in seeker [:cursor] f))

(defn displace [seeker]
  (slicel seeker drop-last))

(defn move-x [seeker f]
  (move seeker
        (fn [[x y]]
          (let [length (-> seeker line count)
                nx (f x)]
            (if (and (>= nx 0) (<= nx length))
              [nx y]
              [x y])))))

(defn move-y [seeker f]
  (move seeker
        (fn [[x y]]
          (let [height (-> seeker :lines count)
                ny (f y)]
            (if (and (>= ny 0) (< ny height))
              [x ny]
              [x y])))))

(defn end-x [seeker]
  (move seeker (fn [[_ y]] [(-> seeker line count) y])))

(defn start-x [seeker]
  (move-x seeker (fn [_] 0)))

(defn left
  ([seeker]
   (left seeker identity))
  ([seeker f]
   (-> seeker (move-x dec) (sym-at) f)))

(defn right
  ([seeker]
   (right seeker identity))
  ([seeker f]
   (-> seeker sym-at f)))

(defn- advance-with [seeker f]
  (let [[_ y] (:cursor seeker)
        h (-> seeker :lines count dec)
        w (-> seeker line count)]
    (m/match [(:cursor seeker)]
             [[w h]] seeker
             [[w _]] (-> seeker (move-y inc) (start-x) f)
             :else (move-x seeker inc))))

(defn- regress-with [seeker f]
  (m/match [(:cursor seeker)]
           [[0 0]] seeker
           [[0 _]] (-> seeker (move-y dec) (end-x) f)
           :else (move-x seeker dec)))

(defn regress [seeker] (regress-with seeker identity))
(defn advance [seeker] (advance-with seeker identity))

(defn climb [seeker]
  (let [offset (move-y seeker dec)]
    (if (sym-at offset)
      offset
      (-> seeker (start-x) (regress)))))

(defn fall [seeker]
  (let [offset (move-y seeker inc)]
    (if (sym-at offset)
      offset
      (-> seeker (move-y inc) (end-x)))))

(defn merge-lines [seeker]
  (peer seeker (fn [l [a b & t]]
                 (-> l
                     (conj (vec (concat a b)))
                     (concat t)))))

(defn rollback [seeker]
  (regress-with seeker merge-lines))

(defn break [seeker]
  (-> seeker
      (slice #(vector %1 %2))
      (peer (fn [l [h & t]] (concat (conj l (first h) (second h)) t)))
      (move-y inc)
      (start-x)))

(def simple-delete (comp rollback displace))
(defn pair-delete [seeker]
  (slice seeker #(concat (drop-last %1) (rest %2))))

(defn pair? [seeker rules]
  (some-> seeker (left #(get rules %)) (= (right seeker))))

(defn auto-delete
  ([seeker]
   (auto-delete seeker matching-rules))
  ([seeker rules]
   (if (pair? seeker rules)
     (-> seeker pair-delete (move-x dec))
     (simple-delete seeker))))

(defn simple-insert [seeker value]
  (-> seeker (slicel #(conj % value)) (move-x inc)))

(defn pair-insert [seeker [key pair]]
  (-> seeker (slicel #(conj % key pair)) (move-x inc)))

(defn auto-insert
  ([seeker key]
   (auto-insert seeker key matching-rules))
  ([seeker key rules]
   (let [pair (get rules key)
         rematched (-> rules (clojure.set/map-invert) (get key))]
     (cond
       (and (nil? pair) (nil? rematched)) (simple-insert seeker key)
       (and (nil? pair) (right seeker #(= % key))) (move-x seeker inc)
       (nil? rematched) (pair-insert seeker [key pair])
       (and (= key rematched) (right seeker #(= % key))) (move-x seeker inc)
       (= key rematched) (pair-insert seeker [key key])
       :else (simple-insert seeker key)))))

(defn do-until [elm f p]
  (let [x (f elm)]
    (if (p x) x (recur x f p))))

(defn start? [seeker]
  (-> seeker :cursor first zero?))

(defn jump [seeker f]
  (do-until seeker f #(or (start? %)
                          (nil? (sym-at %))
                          (= (sym-at %) \space))))

(defn munch                                                 ;; this should be called codelete
  ([seeker] (munch seeker matching-rules))
  ([seeker rules]
   (let [x (advance seeker)]
     (cond
       (= (:cursor seeker) (:cursor x)) seeker
       (pair? seeker rules) (-> seeker (pair-delete) (regress))
       :else (auto-delete x rules)))))

;; It looks right or left and applies the function as long as there is valid input, regardless of it being empty or not
;; It should be a valid line

;; Perhaps make sym-at consider following lines as well
;; Advance and sym-at would actually facilitate this behaviour. The problem is that the char to the
;; right is actually the char where I'm currently at. This would create some difficulties.
;; But if I get this invariant in the function, then returning a result of nil would always represent
;; the fact that I've arrived at the end of the text itself.
;; this will perhaps simplify some things. So if a return of `nil` would represent the end of text
;; then I can reason about these functions a little bit more easily.

(defn inputs [seeker stroke]
  (m/match [stroke]
           [{:key :left :ctrl true}] (jump seeker regress)
           [{:key :right :ctrl true}] (jump seeker advance)
           [{:key :left}] (regress seeker)
           [{:key :right}] (advance seeker)
           [{:key :up}] (climb seeker)
           [{:key :down}] (fall seeker)
           [{:key :backspace}] (auto-delete seeker)
           [{:key :delete}] (munch seeker)
           [{:key :enter}] (break seeker)
           :else (auto-insert seeker (:key stroke))))

(defn stringify [seeker]
  (->> seeker :lines (map #(apply str %)) (apply str)))

(comment
  "cond-> or cond->> will facilitate the behaviour I want. It will just process some boolean config and apply the necessary
functions associated with each appropriate config.")