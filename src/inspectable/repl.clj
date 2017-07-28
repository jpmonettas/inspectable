(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [inspectable.ui.fail-inspector :as fail-inspector]
            [inspectable.ui.spec-browser :as spec-browser]
            [clojure.string :as str]
            [inspectable.core :as core]
            [clojure.walk :as walk]
            
            [org.httpkit.server :as server]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.format :refer [wrap-restful-format]]))

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
  [& args] (apply spec-browser/browse-spec args))

(defn install
  "Install repl-caught as yoru clojure.main/repl-caught fn.
  Every spec exception will be catched so you don't need to explicitly use why."
  []
  (alter-var-root #'clojure.main/repl-caught (constantly repl-caught)))

(defonce server (atom nil))

(defn- server-handler [req]
  (cond
    (and (= :post (:request-method req))
         (= "/pretty-explain" (:uri req)))
    (fail-inspector/pretty-explain (-> req :params :fn-sym)
                                   (-> req :params :ex-data)))
  {:status 200})

(defn start-cljs []
  (reset! server 
          (server/run-server (-> #'server-handler
                                 (wrap-cors :access-control-allow-origin [#".*"]
                                            :access-control-allow-methods [:post])
                                 (wrap-restful-format))
                             {:port 1234})))

(defn stop-cljs []
  (@server))

