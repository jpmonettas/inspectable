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

(defn make-websocket! [url receive-handler]
 (.log js/console "attempting to connect to inspectable websocket to " url)
 (let [chan (js/WebSocket. url)]
   (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
   (set! (.-onerror chan) (fn [e]
                            (js/setTimeout (fn [] (make-websocket! url receive-handler)) 5000)
                            (.log js/console "Inspectable websocket connection failed! Did you run inspectable.repl/start-cljs ?")))
   (set! (.-onopen chan) (fn [e]
                           (reset! ws-chan chan)
                           (.log js/console "Websocket connection established with: " url)))))


;;;;;;;;;;;;;;;;
;; Repl stuff ;;
;;;;;;;;;;;;;;;;

(defn- receive-handler [[event k data]]
  (cond
    (= event :spec-list)
    (send-transit-msg! [:response k (spec-utils/spec-list data)])

    (= event :spec-form)
    (send-transit-msg! [:response k (spec-utils/spec-form data)])

    (= event :spec-sample)
    (send-transit-msg! [:response k (spec-utils/spec-sample data)])))

(make-websocket! "ws://localhost:53427/socket" receive-handler)

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
       (contains? thing :cljs.spec.alpha/value)))

(defn browse-spec
  ""
  ([] (browse-spec ".*"))
  ([spec]
   (send-transit-msg! [:browse-spec {:spec spec}])))

(defn repl-caught [ex env opts]
  (throw (ex-info "No support for clojurescript yet." {})))

(defn install []
  (throw (ex-info "No support for clojurescript yet." {})))


