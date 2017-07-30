(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [inspectable.ui.fail-inspector :as fail-inspector]
            [inspectable.ui.spec-browser :as spec-browser]
            [clojure.string :as str]
            [inspectable.core :as core]
            [clojure.walk :as walk]
            [cognitect.transit :as t]
            [clojure.core.async :as async]            
            [org.httpkit.server :as server]
            [ring.middleware.cors :refer [wrap-cors]]
            [inspectable.spec-utils :as spec-utils])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util UUID]))

(defn- fn-symbol-from-ex-message [ex]
  (let [sym-str (->> (.getMessage ex)
                     (re-find #"Call to #?'?(.+) did not conform to spec:")
                     second)]
    (when sym-str
      (symbol sym-str))))

(defn spec-ex-data? [thing]
  (and (map? thing)
       (or (contains? thing :clojure.spec.alpha/value)
           (contains? thing :cljs.spec.alpha/value))))

(defn repl-caught
  ([] (repl-caught *e))
  ([ex]
   (if-let [[fn-sym spec-ex] (cond
                               (spec-ex-data? (ex-data ex))
                               [(fn-symbol-from-ex-message ex) ex]

                               (spec-ex-data? (ex-data (.getCause ex)))
                               [(fn-symbol-from-ex-message ex) (.getCause ex)])]
     (fail-inspector/pretty-explain fn-sym (ex-data spec-ex))
     (clojure.main/repl-caught ex))))



(defmacro why
  "Tries to run the form and detect clojure.spec fails.
  If form returns ex-data or throws an exception containing it as returned by (clojure.spec/explain-data ...)
  opens a graphical interface trying to explain what went wrong."
  [form]
  (try
    (let [expanded-form (macroexpand form)]
      `(try
         (let [result# ~form]
           (if (spec-ex-data? result#)
             (fail-inspector/pretty-explain nil result#)
             result#))
        (catch Exception e#
          (repl-caught e#))))
    (catch Exception ex
      (repl-caught ex))))

(defn browse-spec
  "Just a wrapper for inspectable.ui.spec-browser, refer to its doc."
  ([] (browse-spec ".*"))
  ([spec]
   (spec-browser/browse-spec spec
                             spec-utils/spec-list
                             spec-utils/spec-form)))

(defn install
  "Install repl-caught as yoru clojure.main/repl-caught fn.
  Every spec exception will be catched so you don't need to explicitly use why."
  []
  (alter-var-root #'clojure.main/repl-caught (constantly repl-caught)))



(defonce server (atom nil))
(defonce server-client-responses (atom {}))
(defonce socket-ch (atom nil))

(defn- server-client-request [event data]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)
        k (str (UUID/randomUUID))
        p (promise)
        _ (t/write writer [event k data])
        str-out (.toString out)]
    (swap! server-client-responses assoc k p)
    (server/send! @socket-ch str-out)
    (deref p 3000 nil)))

(defn- server-client-spec-list [filter-regex]
  (server-client-request :spec-list filter-regex))

(defn- server-client-spec-form [spec]
  (server-client-request :spec-form spec))

(defn- server-handler [req]
  (if (= (:uri req) "/socket")
    (server/with-channel req ch
      (reset! socket-ch ch)
      (server/on-close ch (fn [status] (println "ch closed: " status)))
      (server/on-receive ch (fn [msg]
                              (async/go
                               (println msg)
                               (let [[event & r] (t/read (t/reader (ByteArrayInputStream. (.getBytes msg)) :json))] 
                                 (cond
                                   (= event :pretty-explain) (let [data (first r)]
                                                               (fail-inspector/pretty-explain (:fn-sym data)
                                                                                              (:ex-data data)))
                                   (= event :browse-spec) (let [data (first r)]
                                                            (spec-browser/browse-spec (:spec data)
                                                                                      server-client-spec-list                     
                                                                                      server-client-spec-form))
                                   (= event :response) (let [[k data] r]
                                                         (deliver (get @server-client-responses k) data))))))))
    {:status 404}))


(defn start-cljs []
  (reset! server 
          (server/run-server (-> #'server-handler
                                 (wrap-cors :access-control-allow-origin [#".*"]
                                            :access-control-allow-methods [:post]))
                             {:port 1234})))

(defn stop-cljs []
  (@server))

