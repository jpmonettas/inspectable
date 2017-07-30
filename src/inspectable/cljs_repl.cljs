(ns inspectable.cljs-repl
  (:require [clojure.walk :as walk]
            [cljs.spec.alpha :as s]
            [cognitect.transit :as t]
            [inspectable.spec-utils :as spec-utils]))

;;;;;;;;;;;;;;;;;;;;;;
;; Websockets stuff ;;
;;;;;;;;;;;;;;;;;;;;;;

(defonce ws-chan (atom nil))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))


(defn send-transit-msg!
 [msg]
 (if @ws-chan
   (.send @ws-chan (t/write json-writer msg))
   (throw (js/Error. "Websocket is not available!"))))

(defn receive-transit-msg!
 [update-fn]
 (fn [msg]
   (update-fn
     (->> msg .-data (t/read json-reader)))))

(defn receive-handler [[event k data]]
  (.log js/console "GOT" event)
  (.log js/console "GOT" k)
  (.log js/console "GOT" data)
  (cond
    (= event :spec-list)
    (send-transit-msg! [:response k (spec-utils/spec-list data)])

    (= event :spec-form)
    (send-transit-msg! [:response k (spec-utils/spec-form data)])))

(defn make-websocket! [url receive-handler]
 (.log js/console "attempting to connect websocket")
 (if-let [chan (js/WebSocket. url)]
   (do
     (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
     (reset! ws-chan chan)
     (.log js/console "Websocket connection established with: " url))
   (throw (js/Error. "Websocket connection failed!"))))

(make-websocket! "ws://localhost:1234/socket" receive-handler)

;;;;;;;;;;;;;;;;
;; Repl stuff ;;
;;;;;;;;;;;;;;;;

(defn- fn-symbol-from-ex [ex] 
  (let [sym-str (->> (.-message ex)
                     (re-find #"Call to #?'?(.+) did not conform to spec:")
                     second)]
    (when sym-str
      (symbol sym-str))))

(defn- pretty-explain
  ([ex-data] (pretty-explain nil ex-data))
  ([fn-sym ex-data]
   (send-transit-msg! [:pretty-explain {:ex-data (walk/postwalk (fn [v]
                                                                  (if (or (coll? v)
                                                                          (keyword? v)
                                                                          (string? v)
                                                                          (number? v)
                                                                          (symbol? v))
                                                                    v
                                                                    (str v)))
                                                                ex-data)
                                        :fn-sym fn-sym}])))

(defn spec-ex-data? [thing]
  (and (map? thing)
       (or (contains? thing :clojure.spec.alpha/value)
           (contains? thing :cljs.spec.alpha/value))))

(defn browse-spec
  ""
  ([] (browse-spec ".*"))
  ([spec]
   (send-transit-msg! [:browse-spec {:spec spec}])))

#_(defn repl-caught [ex env opts]
  (if-let [[fn-sym spec-ex] (cond
                              (spec-ex-data? (ex-data ex))
                              [(fn-symbol-from-ex ex) ex])]
    (pretty-explain fn-sym (ex-data spec-ex))
    (cljs-repl/repl-caught ex env opts)))


#_(defn install
    "Install repl-caught as yoru clojure.main/repl-caught fn.
  Every spec exception will be catched so you don't need to explicitly use why."
    [])


