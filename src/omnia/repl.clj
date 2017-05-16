(ns omnia.repl
  (use omnia.more)
  (require
    ritz.nrepl.middleware.javadoc
    ritz.nrepl.middleware.simple-complete
    [clojure.tools.nrepl.server :as s]
    [clojure.tools.nrepl :as nrepl]
    [omnia.input :as i]
    [clojure.core.match :as m]
    [clojure.string :refer [split trim-newline]]
    [omnia.formatting :as f]
    [clojure.edn :as edn]))

(comment
  ;; FIXME
  " 1. Add nrepl server start and stop. // done
    2. Refactor repl initiation // done
    3. Add preloading of functions and dependencies. // done
    4. Add repl session loading.")

(defrecord REPL [eval-f complete-f stop-f history hsize timeline result])

(def ritz-middleware
  [#'ritz.nrepl.middleware.javadoc/wrap-javadoc
   #'ritz.nrepl.middleware.simple-complete/wrap-simple-complete])

(def predef
  (->> (str '(require '[omnia.resolution :refer [retrieve retrieve-from]]))
       (i/str->lines)
       (i/seeker)))

(defn- out? [response]
  (contains? response :out))

(defn- err? [response]
  (contains? response :err))

(defn- ex? [response]
  (contains? response :ex))

(defn- eff? [response]
  (and (contains? response :value)
       (nil? (:value response))))

(defn- response->lines [response]
  (cond
    (out? response) (-> response (:out) (f/fmt-edn) (i/str->lines))
    (err? response) (-> response (:err) (i/str->lines))
    (eff? response) [[\n \i \l]]
    (ex? response) []
    :else (-> response (:value) (str) (f/fmt-edn) (i/str->lines))))

(defn- suggestion [responses]
  (->> responses
       (first)
       (:value)
       (str)
       (edn/read-string)
       (first)
       (map i/str->lines)
       (reduce concat)
       (vec)
       (i/seeker)))

(defn- seekify-responses [responses]
  (->> responses
       (map response->lines)
       (apply i/join-lines)
       (i/seeker)))

(defn- connect [host port timeout]
  (fn [msg transform]
    (with-open [conn (nrepl/connect :host host :port port)]
      (-> (nrepl/client conn timeout)
          (nrepl/message msg)
          (transform)))))

(defn- eval-msg [seeker]
  {:op :eval
   :code (i/stringify seeker)})

(defn- complete-msg [seeker]
  {:op :complete
   :symbol (-> seeker
               (i/expand-word)
               (i/extract)
               (i/stringify)
               (trim-newline))
   :ns (ns-name *ns*)})

(defn- cache-result [repl result]
  (update repl :result (fn [_] result)))

(defn- remember [repl seeker]
  (let [lines (:lines seeker)]
    (if (or (i/is-empty? seeker)
            (-> lines first empty?))
      repl
      (-> repl
          (update :history #(conj % seeker))
          (update :hsize inc)))))

(defn- reset-timeline [repl]
  (update repl :timeline (fn [_] (:hsize repl))))

(defn travel-back [repl]
  (update repl :timeline #(bound-dec % 0)))

(defn travel-forward [repl]
  (update repl :timeline #(bound-inc % (:hsize repl))))

(defn then [repl]
  (nth (:history repl) (:timeline repl) i/empty-seeker))

(defn result [repl]
  (:result repl))

(defn evaluate [repl seeker]
  (let [f (:eval-f repl)]
    (-> repl
        (remember seeker)
        (cache-result (f seeker))
        (reset-timeline))))

(defn complete [repl seeker]
  (let [f (:complete-f repl)]
    (f seeker)))

(defn stop [repl] ((:stop-f repl)))

(defn- repl-with [eval-f complete-f stop-f]
  (REPL. eval-f complete-f stop-f [i/empty-seeker] 1 0 i/empty-seeker))

(defn repl [{:as   params
             :keys [kind port host timeout]
             :or   {kind    :local
                    timeout 5000                           ;; fixme: kill infinte processes and return warning
                    port    11111
                    host    "localhost"}}]
  (assert (map? params) "Input to `repl` must be a map.")
  (case kind
    :identity (repl-with identity identity (fn [] nil))
    :remote (repl-with (connect host port timeout) identity (fn [] nil))
    :local (let [handler (apply s/default-handler ritz-middleware)
                 server (s/start-server :port port
                                        :handler handler)
                 send-f (connect "localhost" port timeout)
                 eval-f #(send-f (eval-msg %) seekify-responses)
                 comp-f #(send-f (complete-msg %) suggestion)
                 stop-f #(s/stop-server server)]
             (eval-f predef)
             (repl-with eval-f comp-f stop-f))))

