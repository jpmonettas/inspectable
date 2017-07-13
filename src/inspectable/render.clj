(ns inspectable.render
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]
            [seesaw.core :as ss]
            [seesaw.color :as sscolor]
            [seesaw.tree :as sstree]
            [seesaw.mig :as ssmig]
            [inspectable.core :as core]
            [clojure.spec.gen.alpha :as gen])
  (:import [javax.swing.tree TreeCellRenderer TreeModel TreePath]
           [javax.swing JTree]))

(defn problems-paths
  ([^TreeModel tm] (problems-paths tm (.getRoot tm)))
  ([^TreeModel tm node]
   (if (-> node meta :node-problems)
     (list (list node)) 
     (when-not (.isLeaf tm node)
       (let [child-paths (->> (range (.getChildCount tm node))
                              (keep (fn [idx]
                                      (problems-paths tm (.getChild tm node idx))))
                              (reduce concat))]
         (when (seq child-paths)
           (map #(conj % node) child-paths)))))))

(defn format-val [val]
  (cond
    (instance? clojure.lang.IMapEntry val) (str val)
    (coll? val) (str (empty val))
    true (str val)))

(defn map-contains-pred? [pred]
  (and (seq? pred)
       (= (first pred) 'clojure.core/fn)
       (= (-> pred (nth 2) first) 'clojure.core/contains?)))

(defn format-problem [problem]
  (let [pred (:pred problem)]
    (cond
      (map-contains-pred? pred)
      (format "Missing required key <b>%s</b>" (-> pred (nth 2) (nth 2)))
      
      true (format "<b>%s</b>" pred))))


(defn make-problem-node [val val-problems]
  (ss/flow-panel
   :hgap 20
   :align :left
   :items [(ss/label :text (format-val val)
                     :foreground (sscolor/color "#a94442")
                     :font {:name :monospaced :size 15})
           (ss/label :text (str "<html> fails for "
                                (->> val-problems
                                     (map format-problem)
                                     (str/join " and "))
                                "</html>")
                     :font {:name :monospaced :size 15})]))

(defn make-ok-node [val in-problem-path?]
  (ss/flow-panel
   :background (sscolor/color "white")
   :align :left
   :items
   [(ss/label :text (format-val val)
              :font {:name :monospaced :size 15}
              :foreground (if (or in-problem-path?
                                  (and (instance? clojure.lang.IMapEntry val)
                                       (or (-> val first meta :in-problem-path?)
                                           (-> val second meta :in-problem-path?))))
                            (sscolor/color "#a94442")
                            (sscolor/color "#CCCCCC")))]))

(def node-renderer
  (reify TreeCellRenderer
    (getTreeCellRendererComponent [this component value selected? expanded? leaf? row focus?]
      (let [{:keys [in-problem-path? wrapped-val node-problems]} (meta value)]
        (if node-problems 
          (make-problem-node (or wrapped-val value) node-problems)
          (make-ok-node (or wrapped-val value) in-problem-path?))))))

(defn build-tree-path [node-path]
  (let [tree-path (TreePath. (first node-path))]
    (reduce
     (fn [tp n]
       (.pathByAddingChild tp n))
     tree-path
     (rest node-path))))

(defn make-tree [annotated-value]
  (let [^JTree tree (ss/tree :id :tree
                      :model (sstree/simple-tree-model coll? seq annotated-value)
                      :renderer node-renderer)
        problems-paths (problems-paths (.getModel tree))]
    (.setExpandsSelectedPaths tree true)
    (.setSelectionPaths tree (into-array TreePath (map build-tree-path problems-paths)))
    tree))


(defn pretty-explain [title #:clojure.spec.alpha{:keys [problems value]}]
  (let [ann-value (core/annotate-data value problems)]
    (-> (ss/frame :title "Spec failed"
                  :content (ss/border-panel
                            :vgap 30
                            :background (sscolor/color "#f2dede")
                            :north (ss/text :text title
                                            :multi-line? true
                                            :background (sscolor/color "#f2dede")
                                            :font {:name :monospaced :size 15}
                                            :foreground (sscolor/color "#a94442"))
                            :center (make-tree ann-value)) )
        ss/pack!
        ss/show!)))

(defn pretty-explain-fn-fail [fn-name #:clojure.spec.alpha{:keys [problems value]}]
  (let [ann-args (core/annotate-data value problems)]
    (-> (ss/frame :title "Spec failed"
                  :content (ss/border-panel
                            :vgap 30
                            :background (sscolor/color "#f2dede")
                            :north (ss/label :text (format "<html>Error calling <b>%s</b></html>" fn-name)
                                            :background (sscolor/color "#f2dede")
                                            :font {:name :monospaced :size 15}
                                            :foreground (sscolor/color "#a94442"))
                            :center (ss/scrollable
                                     (ssmig/mig-panel
                                      :background (sscolor/color "white")
                                      :items (map-indexed (fn [i ann-arg]
                                                            [(ssmig/mig-panel
                                                              :items [[(ss/label (str "arg" i)) "wrap"]
                                                                      [(make-tree ann-arg) "wrap"]])
                                                             "wrap"])
                                                          (reverse ann-args))))))
        ss/pack!
        ss/show!)))

(comment
;;;;;;;;;;;;;;;
;; Repl test ;;
;;;;;;;;;;;;;;;

  (s/def :user/name (s/and string?
                           #(= (str/capitalize %) %)))
  (s/def :user/age pos-int?)
  (s/def :user/numbers (s/coll-of (s/or :n (s/and int? even?)
                                        :c string?) :kind vector?))
  (s/def ::user (s/keys :req [:user/name
                              :user/age
                              :user/numbers]))

  (gen/generate (s/gen ::user))
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

  (try
    (users-older-than users 5 6 '(12 3 43 45))
    
    (catch Exception e
      (pretty-explain-fn-fail (->> (.getMessage e)
                                   (re-find #"Call to #'(.+) did not conform to spec:")
                                   second)
                      (ex-data e))))

     
  )

