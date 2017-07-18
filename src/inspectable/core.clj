(ns inspectable.core
  (:require [clojure.walk :as walk]))

(defn sub-path?
  "Is p1 a sub-path of p2"
  [p1 p2]
  (and (every? (partial apply =) (map vector p1 p2))
       (<= (count p1) (count p2))))

(defn postwalk-with-paths
  ([form f] (postwalk-with-paths form f [] []))
  ([form f in-path obj-path]
   (let [obj-path' (conj obj-path form)]
    (cond
      ;; Map entries
      (instance? clojure.lang.IMapEntry form)
      (let [[k v] form]
        ;; TODO Remove this hack as soon as 
        ;; https://dev.clojure.org/jira/browse/CLJ-2192 is resolved
        (if (keyword? k)
          (clojure.lang.MapEntry. k (postwalk-with-paths v f (into in-path [k]) obj-path'))
          (clojure.lang.MapEntry. (postwalk-with-paths k f (into in-path [k 0]) obj-path')
                                  (postwalk-with-paths v f (into in-path [k 1]) obj-path'))))

      ;; Maps or Records
      (or (instance? clojure.lang.IRecord form)
          (map? form))
      (f (reduce (fn [r x] (conj r (postwalk-with-paths x f in-path obj-path'))) (empty form) form)
         in-path
         obj-path)

      (seq? form) (f (doall (map-indexed #(postwalk-with-paths %2 f (conj in-path %1) obj-path') form))
                      in-path
                      obj-path)
      
      ;; All coll? [] #{} 
      (coll? form) (f (into (empty form) (map-indexed #(postwalk-with-paths %2 f (conj in-path %1) obj-path') form))
                      in-path
                      obj-path)

      ;; keyword?, number?, string? ...
      :else (f form in-path obj-path)))))

(defrecord AnnWrapper [value data])

(defn with-annotation [x ann-data]
  (->AnnWrapper x ann-data))

(defn annotate-data [value problems ann-problem-path?]
  (postwalk-with-paths
   value
   (fn [v path obj-path]
     (cond
       (some #(= path (:in %)) problems)
       (let [node-problems (filter #(= path (:in %)) problems)]
         (with-annotation v {:node-problems node-problems
                             :in-problem-path? true}))
       
       (and ann-problem-path?
            (some #(sub-path? path (:in %)) problems))
       (with-annotation v {:in-problem-path? true})
       
       :else v))))

(defn deannotate-data [value]
  (walk/postwalk
   (fn [node]
     (if (instance? inspectable.core.AnnWrapper node)
       (:value node)
       node))
   value))



