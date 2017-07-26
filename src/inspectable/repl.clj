(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [inspectable.ui.fail-inspector :as fail-inspector]
            [inspectable.ui.spec-browser :as spec-browser]
            [clojure.string :as str]
            [inspectable.core :as core]
            [clojure.walk :as walk]))

(defn- fn-symbol-from-ex-message [ex]
  (let [sym-str (->> (.getMessage ex)
                     (re-find #"Call to #?'?(.+) did not conform to spec:")
                     second)]
    (when sym-str
      (symbol sym-str))))

(defn repl-caught
  ([] (repl-caught *e))
  ([ex]
   (if-let [[fn-sym spec-ex] (cond
                               (contains? (ex-data ex) :clojure.spec.alpha/value)
                               [(fn-symbol-from-ex-message ex) ex]
                               (contains? (ex-data (.getCause ex)) :clojure.spec.alpha/value)
                               [(fn-symbol-from-ex-message ex) (.getCause ex)])]
     (fail-inspector/pretty-explain fn-sym (ex-data spec-ex))
     (clojure.main/repl-caught ex))))

(defn ex-data? [thing]
  (and (map? thing)
       (contains? thing :clojure.spec.alpha/value)))

(defmacro why
  "Tries to run the form and detect clojure.spec fails.
  If form returns ex-data or throws and expression containing it as returned by (clojure.spec/ex-data ...)
  opens a graphical interface trying to explain what went wrong."
  [form]
  (try
    (let [expanded-form (macroexpand form)]
      `(try
         (let [result# ~form]
           (if (ex-data? result#)
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

(comment
;;;;;;;;;;;;;;;
;; Repl test ;;
;;;;;;;;;;;;;;;
  (browse-spec)
  (browse-spec "clojure.core/l")
  (browse-spec 'clojure.core/let)
  

  (s/def :user/name (s/and string?
                           #(= (str/capitalize %) %)))
  (s/def :user/age pos-int?)
  (s/def :user/numbers (s/coll-of (s/and int? even?) :kind vector?))
  (s/def ::user (s/keys :req [:user/name
                              :user/age
                              :user/numbers]))

  (def users
    [#:user{:name "Alice"
             :age 20
            :numbers [2]}
     #:user{:name "Aohn"
            :age 33
            :numbers [9]}
     #:user{:name "Bob"
            :age 52
            :numbers [2]}])
  (why (s/explain-data ::user #:user{:name "Alice"
                                     ;; :age 20
                                     :numbers [2]}))
  
  (why (s/explain-data (s/coll-of ::user :kind vector?) users))

  (stest/instrument)

  (s/fdef users-older-than
        :args (s/cat :users (s/coll-of ::user :kind vector?)
                     :age :user/age
                     :other any?
                     :rest any?)
        :ret ::user)
  
  (defn users-older-than [users age bla & r])


  (why (users-older-than users 5 6 '(12 3 43 45)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (s/def ::tag #{:a :b :c})
  (s/def ::tagged-data (s/cat :tag ::tag :content (s/* (s/alt :n int? :s string?))))
  (s/def ::all (s/coll-of ::tagged-data :kind vector?))


  (why (s/explain-data ::all [[:a 1 2 3 "test" 2]
                                         [:b 1 "b" true "test" 2]
                                         [:c "a" 2 3 "test" 2]
                                         [:c "a" 2 3 "test" 2]
                                         [:c "a" 2 3 "test" 2]]))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  (why (let [a 5
           4]
       5))
  
  (why (ns bla
         (:requir clojure.pprint)))
  
  (why (defn f (a) (+ 1 a)))
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (clojure.main/repl :caught inspectable.repl/repl-caught)

  )

