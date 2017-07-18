(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [inspectable.ui.fail-inspector :refer [pretty-explain-fn-fail
                                                   pretty-explain]]
            [inspectable.ui.spec-browser :as spec-browser]
            [clojure.string :as str]
            [inspectable.core :as core]))


(defn repl-caught
  ([] (repl-caught *e))
  ([ex]
   (when (contains? (ex-data ex) :clojure.spec.alpha/value)
     (pretty-explain
      (->> (.getMessage ex)
           (re-find #"Call to #?'?(.+) did not conform to spec:")
           second
           symbol)
      (ex-data ex)))))

(defmacro i [form]
  (try
    (let [expanded-form (macroexpand form)]
     `(try
        ~form
        (catch Exception e#
          (repl-caught e#))))
    (catch Exception compiler-ex
      (repl-caught (.getCause compiler-ex)))))

(defn browse-spec [thing]
  (spec-browser/browse-spec thing))

(comment
;;;;;;;;;;;;;;;
;; Repl test ;;
;;;;;;;;;;;;;;;
  (browse-spec "user")

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
     #:user{:name "John"
            :age 33
            :numbers [9]}
     #:user{:name "Bob"
            :age 52
            :numbers [2]}])
  (pretty-explain  (s/explain-data (s/coll-of ::user :kind vector?) users))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (s/def ::tag #{:a :b :c})
  (s/def ::tagged-data (s/cat :tag ::tag :content (s/* (s/alt :n int? :s string?))))
  (s/def ::all (s/coll-of ::tagged-data :kind vector?))


  (pretty-explain (s/explain-data ::all [[:a 1 2 3 "test" 2]
                                         [:b 1 "b" true "test" 2]
                                         [:c "a" 2 3 "test" 2]]))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (stest/instrument)

  (s/fdef users-older-than
        :args (s/cat :users (s/coll-of ::user :kind vector?)
                     :age :user/age
                     :other any?
                     :rest any?)
        :ret ::user)
  
  (defn users-older-than [users age bla & r])


  (i
   (users-older-than users 5 6 '(12 3 43 45)))

  (i (let [a 5
           4 8]
       5))

  (inspectable.repl/i
   (let [ann-args (core/annotate-data value problems)
         z 3
         b 4
         ann-args2 (core/annotate-data value problems)
         3]
     {:a 1
      :b 2
      :c 100}
     ))
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  (clojure.main/repl :caught )

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (if (contains? (ex-data ex) :clojure.spec.alpha)
        (log/error ex "Uncaught exception on" (.getName thread))))))
  )

