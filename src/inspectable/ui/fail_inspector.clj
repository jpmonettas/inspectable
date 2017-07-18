(ns inspectable.ui.fail-inspector
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as str]
            [seesaw.core :as ss]
            [seesaw.color :as sscolor]
            [seesaw.tree :as sstree]
            [seesaw.mig :as ssmig]
            [inspectable.core :as core]
            [clojure.spec.gen.alpha :as gen]
            clojure.pprint)
  (:import [javax.swing.tree TreeCellRenderer TreeModel TreePath]
           [javax.swing JTree]
           javax.swing.JEditorPane))

(defn problems-paths
  ([^TreeModel tm] (problems-paths tm (.getRoot tm)))
  ([^TreeModel tm node]
   (if (:node-problems node)
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
    (instance? clojure.lang.IMapEntry val) (str [(first val) " ..."])
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

(defn format-problems [problems]
  (str "<span style=\"background-color: #CCCCCC;\">fails for "
       (->> problems
            (map format-problem)
            (str/join " and "))
       "</span>"))

(defn make-problem-node [val val-problems]
  (ss/flow-panel
   :hgap 20
   :align :left
   :items [(ss/label :text (format-val val)
                     :foreground (sscolor/color "#a94442")
                     :font {:name :monospaced :size 15})
           (ss/label :text (str "<html>" (format-problems val-problems) "</html>")
                     :font {:name :monospaced :size 15})]))

(defn make-ok-node [val in-problem-path?]
  (ss/flow-panel
   :background (sscolor/color "white")
   :align :left
   :items
   [(ss/label :text (format-val val)
              :font {:name :monospaced :size 15}
              :foreground (if in-problem-path?
                            (sscolor/color "#a94442")
                            (sscolor/color "#CCCCCC")))]))

(defn build-tree [node]
  (when node
    (cond 
      (instance? clojure.lang.IMapEntry node)
      {:val (clojure.lang.MapEntry. (core/deannotate-data (first node))
                                    (core/deannotate-data (second node)))
       :in-problem-path? (or (-> node first :data :in-problem-path?)
                             (-> node second :data :in-problem-path?))
       :children (map build-tree node)}
      
      (instance? inspectable.core.AnnWrapper node)
      {:val (core/deannotate-data (:value node))
       :in-problem-path? (-> node :data :in-problem-path?)
       :node-problems (-> node :data :node-problems)
       :children (when (coll? (:value node)) (map build-tree (:value node)))}

      true
      {:val (core/deannotate-data node)
      :children (when (coll? node) (map build-tree node))})))

(def node-renderer
  (reify TreeCellRenderer
    (getTreeCellRendererComponent [this component value selected? expanded? leaf? row focus?]
      (let [{:keys [in-problem-path? val node-problems]} value]
        (if node-problems 
          (make-problem-node val node-problems)
          (make-ok-node val in-problem-path?))))))

(defn build-tree-path [node-path]
  (let [tree-path (TreePath. (first node-path))]
    (reduce
     (fn [tp n]
       (.pathByAddingChild tp n))
     tree-path
     (rest node-path))))


(defn make-tree [annotated-value]
  (let [^JTree tree (ss/tree :id :tree
                             :model (sstree/simple-tree-model :children
                                                              :children
                                                              (build-tree annotated-value))
                             :renderer node-renderer)
        problems-paths (problems-paths (.getModel tree))]
    (.setExpandsSelectedPaths tree true)
    (.setSelectionPaths tree (into-array TreePath (map build-tree-path problems-paths)))
    tree))


(defn value-as-args-tree-panel [value problems]
  (ssmig/mig-panel
   :background (sscolor/color "white")
   :items (map-indexed (fn [i ann-arg]
                         [(ssmig/mig-panel
                           :items [[(ss/label (str "arg" i)) "wrap"]
                                   [(make-tree ann-arg) "wrap"]])
                          "wrap"])
                       ;; TODO do something about this
                       (let [ann (core/annotate-data value problems false)]
                         (if (instance? inspectable.core.AnnWrapper ann)
                           (:value ann)
                           ann)))))

(defn value-as-tree-panel [value problems]
  (make-tree ;; TODO do something about this
   (let [ann (core/annotate-data value problems false)]
     (if (instance? inspectable.core.AnnWrapper ann)
       (:value ann)
       ann))))

(defmethod clojure.pprint/code-dispatch inspectable.core.AnnWrapper
  [w]
  (if-let [problems (-> w :data :node-problems)]
    (clojure.pprint/pprint-logical-block :prefix "<span style=\"color: red;\">"
                                         :suffix (str "</span>&nbsp;" (format-problems problems))
                                         (clojure.pprint/write-out (:value w)))
    (clojure.pprint/write-out (:value w))))

(defn value-as-pp-panel [value problems fail-form-sym]
  (doto (ss/editor-pane :content-type "text/html"
                        :editable? false
                        :font {:name :monospaced :size 15}
                        :text (str "<pre>"
                                   (clojure.pprint/write (cond-> (core/annotate-data value problems false)
                                                           fail-form-sym (conj fail-form-sym))
                                                         :stream nil
                                                         :dispatch clojure.pprint/code-dispatch)
                                   "</pre>"))
    (.putClientProperty JEditorPane/HONOR_DISPLAY_PROPERTIES true)))

(defn pretty-explain
  ([ex-data] (pretty-explain nil ex-data))
  ([fail-form-sym #:clojure.spec.alpha{:keys [problems value]}]
   (-> (ss/frame :title "Inspectable"
                 :content (ss/border-panel
                           :vgap 30
                           :background (sscolor/color "#f2dede")
                           :north (ss/label :text (if fail-form-sym
                                                    (format "<html>Error calling <b>%s</b></html>" fail-form-sym)
                                                    "Spec failed") 
                                            :background (sscolor/color "#f2dede")
                                            :font {:name :monospaced :size 15}
                                            :foreground (sscolor/color "#a94442"))
                           :center (ss/tabbed-panel :placement :top
                                                    :overflow :scroll
                                                    :tabs (cond-> []
                                                            (not fail-form-sym) (conj {:title "Tree"
                                                                                       :content (value-as-tree-panel value problems)})
                                                            fail-form-sym       (conj {:title "Args trees"
                                                                                       :content (value-as-args-tree-panel value problems)})
                                                            true                (conj {:title "Pretty print"
                                                                                       :content (value-as-pp-panel value problems fail-form-sym)})))))
       ss/pack!
       ss/show!)))

