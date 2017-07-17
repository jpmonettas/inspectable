(ns inspectable.repl
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [inspectable.ui.fail-inspector :refer [pretty-explain-fn-fail
                                                   pretty-explain]]
            [inspectable.ui.spec-browser :as spec-browser]
            [clojure.string :as str]))


(defn repl-caught
  ([] (repl-caught *e))
  ([ex]
   (when (contains? (ex-data ex) :clojure.spec.alpha/failure)
     (pretty-explain-fn-fail
      (->> (.getMessage ex)
           (re-find #"Call to #'(.+) did not conform to spec:")
           second)
      (ex-data ex)))))

(defmacro i [form]
  (try
    (let [expanded-form (macroexpand form)]
     `(try
        ~form
        (catch Exception e#
          (repl-caught e#))))
    (catch Exception compiler-ex
      (->> (.getCause compiler-ex)
          ex-data
          (pretty-explain (str "Error compiling " form))))))

(defn browse-spec [thing]
  (spec-browser/browse-spec thing))

(comment
;;;;;;;;;;;;;;;
;; Repl test ;;
;;;;;;;;;;;;;;;

  (s/def :user/name (s/and string?
                           #(= (str/capitalize %) %)))
  (s/def :user/age pos-int?)
  (s/def :user/numbers (s/coll-of (s/and int? even?) :kind vector?))
  (s/def ::user (s/keys :req [:user/name
                              :user/age
                              :user/numbers]))

  (def users
    [#:user{:name "Alice"
            ;; :age 20
            :numbers [2]}
     #:user{:name "John"
            :age 33
            :numbers [2 4 6 8 9]}
     #:user{:name "Bob"
            :age 52
            :numbers [2 3]}])
  (s/explain-data (s/coll-of ::user :kind vector?) users)
  (pretty-explain (s/explain-data (s/coll-of ::user :kind vector?) users))

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
  (pretty-explain (s/explain-data (s/coll-of ::user :kind vector?) users))
  (defn users-older-than [users age bla & r])


  (i
   (users-older-than users 5 6 '(12 3 43 45)))

  (i (let [a 5
           4 8]
       5))
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  
  (clojure.main/repl :caught )

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (if (contains? (ex-data ex) :clojure.spec.alpha)
        (log/error ex "Uncaught exception on" (.getName thread))))))
  )

