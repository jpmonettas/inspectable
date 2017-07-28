(ns inspectable.test
  (:require  [clojure.test :as t]
             [clojure.spec.alpha :as s]
             [clojure.string :as str]
             clojure.spec.test.alpha))

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


(s/fdef users-older-than
        :args (s/cat :users (s/coll-of ::user :kind vector?)
                     :age :user/age
                     :other any?
                     :rest any?)
        :ret ::user)

(defn users-older-than [users age bla & r])


(defn foo []
  (users-older-than users 5 6 '(12 3 43 45)))

(defn bar []
  (foo))

(bar)

(clojure.spec.test.alpha/instrument)
