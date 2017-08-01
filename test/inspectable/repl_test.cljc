(ns inspectable.repl-test
  (:require #?(:clj [inspectable.repl :refer [why browse-spec]]
               :cljs [inspectable.cljs-repl
                      :refer-macros [why]
                      :refer [browse-spec]])
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]))


;;;;;;;;;;;;;;;;;;;;;;;;
;; Some example specs ;;
;;;;;;;;;;;;;;;;;;;;;;;;
 
(s/def :user/name (s/and string?
                         #(= (str/capitalize %) %)))
(s/def :user/age pos-int?)
(s/def :user/numbers (s/coll-of (s/and int? even?) :kind vector?))
(s/def ::user (s/keys :req [:user/name
                            :user/age
                            :user/numbers]))

(s/fdef users-older-than
        :args (s/cat :users (s/coll-of ::user :kind vector?)
                     :age :user/age)
        :ret ::user)

(defn users-older-than [users age])

;;-----------------------------------------------

  (s/def ::tag #{:a :b :c})
  (s/def ::tagged-data (s/cat :tag ::tag :content (s/* (s/alt :n int? :s string?))))
  (s/def ::tags-collection (s/coll-of ::tagged-data :kind vector?))

;;-----------------------------------------------
;;;;;;;;;;;;;;;;;;;;;;;
;; Interactive tests ;;
;;;;;;;;;;;;;;;;;;;;;;;

(defn test-missing-key []
  (why (s/explain-data ::user #:user{:name "Alice"
                                     ;; :age 20
                                     :numbers [2]})))

(defn test-deep-1 []
  (why (s/explain-data (s/coll-of ::user :kind vector?)
                       [#:user{:name "Alice"
                               :age 20
                               :numbers [2]}
                        #:user{:name "Aohn"
                               :age 33
                               :numbers [9]}
                        #:user{:name "Bob"
                               :age 52
                               :numbers [2]}])))

(defn test-deep-2 []
  (why (s/explain-data ::tags-collection [[:a 1 2 3 "test" 2]
                                          [:b 1 "b" true "test" 2]
                                          [:c "a" 2 3 "test" 2]
                                          [:c "a" 2 3 "test" 2]
                                          [:c "a" 2 3 "test" 2]])))
(defn test-browse []
  (browse-spec)
  (browse-spec ::user)
  (browse-spec "clojure.core/l")
  (browse-spec 'clojure.core/let))

(defn test-invalid-fn-call []
  (why (users-older-than [#:user{:name "Alice"
                               :age 20
                               :numbers [2]}
                        #:user{:name "Aohn"
                               :age 33
                               :numbers [9]}
                        #:user{:name "Bob"
                               :age 52
                               :numbers [2]}]
                         5)))


(comment
  (stest/instrument)

  ;; Clojure
  (test-missing-key)
  (test-deep-1)
  (test-deep-2)
  (test-browse)
  (test-invalid-fn-call)
  
  ;; Clojurescript
  (test-missing-key)
  (test-deep-1)
  (test-deep-2)
  (test-browse)
  (test-invalid-fn-call)
  
  )
