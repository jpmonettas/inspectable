(ns inspectable.cljs-repl
  (:require [clojure.walk :as walk]
            [ajax.core :refer [GET POST]]
            [cljs.spec.alpha :as s]))

(defn- fn-symbol-from-ex [ex] 
  (let [sym-str (->> (.-message ex)
                     (re-find #"Call to #?'?(.+) did not conform to spec:")
                     second)]
    (when sym-str
      (symbol sym-str))))

(defn- pretty-explain
  ([ex-data] (pretty-explain nil ex-data))
  ([fn-sym ex-data]
   (POST "http://localhost:1234/pretty-explain"
         {:params {:ex-data (walk/postwalk (fn [v]
                                             (if (or (coll? v)
                                                     (keyword? v)
                                                     (string? v)
                                                     (number? v)
                                                     (symbol? v))
                                               v
                                               (str v)))
                                           ex-data)
                   :fn-sym fn-sym}})))

(defn spec-ex-data? [thing]
  (and (map? thing)
       (or (contains? thing :clojure.spec.alpha/value)
           (contains? thing :cljs.spec.alpha/value))))

#_(defn repl-caught [ex env opts]
  (if-let [[fn-sym spec-ex] (cond
                              (spec-ex-data? (ex-data ex))
                              [(fn-symbol-from-ex ex) ex])]
    (pretty-explain fn-sym (ex-data spec-ex))
    (cljs-repl/repl-caught ex env opts)))

#_(defn browse-spec
  "Just a wrapper for inspectable.ui.spec-browser, refer to its doc."
  [& args] (apply spec-browser/browse-spec args))

#_(defn install
    "Install repl-caught as yoru clojure.main/repl-caught fn.
  Every spec exception will be catched so you don't need to explicitly use why."
    [])

(comment

  
  (s/explain-data int? 123)
  )
