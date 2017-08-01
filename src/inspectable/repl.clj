(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [inspectable.server-client :as server-client]
            [inspectable.spec-utils :as spec-utils]
            [inspectable.ui.fail-inspector :as fail-inspector]
            [inspectable.ui.spec-browser :as spec-browser]))

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
                             spec-utils/spec-form
                             spec-utils/spec-sample)))

(defn install
  "Install repl-caught as yoru clojure.main/repl-caught fn.
  Every spec exception will be catched so you don't need to explicitly use why."
  []
  (alter-var-root #'clojure.main/repl-caught (constantly repl-caught)))

(defn start-cljs []
  (server-client/start-cljs))

(defn stop-cljs []
  (server-client/stop-cljs))

