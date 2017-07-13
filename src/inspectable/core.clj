(ns inspectable.core)

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
      (f (let [[k v] form]
           ;; here is the HACK <------------------
           (if (keyword? k)
             [k (postwalk-with-paths v f (into in-path [k]) obj-path')]
             [(postwalk-with-paths k f (into in-path [k 0]) obj-path')
              (postwalk-with-paths v f (into in-path [k 1]) obj-path')]))
         in-path
         obj-path)

      ;; Maps or Records
      (or (instance? clojure.lang.IRecord form)
          (map? form))
      (f (reduce (fn [r x] (conj r (postwalk-with-paths x f in-path obj-path'))) (empty form) form)
         in-path
         obj-path)

      ;; All coll? [] #{} ()
      (coll? form) (f (into (empty form) (map-indexed #(postwalk-with-paths %2 f (conj in-path %1) obj-path') form))
                      in-path
                      obj-path)

      ;; keyword?, number?, string? ...
      :else (f form in-path obj-path)))))


(defn with-annotation [x ann-data]
  (if (or (symbol? x) (coll? x))
    (with-meta x ann-data)
    (with-meta '■
      (assoc ann-data :wrapped-val x))))

(defn annotate-data [value problems]
  (postwalk-with-paths
   value
   (fn [v path obj-path]
     (cond
       (some #(= path (:in %)) problems)
       (let [node-problems (filter #(= path (:in %)) problems)]
         (with-annotation v {:node-problems node-problems
                             :in-problem-path? true}))
       
       (some #(sub-path? path (:in %)) problems)
       (with-annotation v {:in-problem-path? true})
       
       :else v))))


