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
            [inspectable.ui.spec-browser :as spec-browser]
            [inspectable.ui.themes :refer [get-color]]
            [inspectable.utils :as utils]
            [pretty-spec.core :as pr-spec]
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

(defn make-problem-node [val val-problems]
  (ss/flow-panel
   :hgap 20
   :align :left
   :items [(ss/label :text (format-val val)
                     :foreground (sscolor/color (get-color :problem-foreground))
                     :font {:name :monospaced :size 15})
           (ss/label :text (str "<html>" (utils/format-problems val-problems) "</html>")
                     :font {:name :monospaced :size 15})]))

(defn make-ok-node [val in-problem-path?]
  (ss/flow-panel
   :background (sscolor/color (get-color :tree-background))
   :align :left
   :items
   [(ss/label :text (format-val val)
              :font {:name :monospaced :size 15}
              :foreground (if in-problem-path?
                            (sscolor/color (get-color :problem-foreground))
                            (sscolor/color (get-color :ok-node-foreground))))]))

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
  (ss/scrollable
   (ssmig/mig-panel
    :background (sscolor/color (get-color :tree-background))
    :items (map-indexed (fn [i ann-arg]
                          [(ssmig/mig-panel
                            :items [[(ss/label (str "arg" i)) "wrap"]
                                    [(make-tree ann-arg) "wrap"]])
                           "wrap"])
                        ;; TODO do something about this
                        (let [ann (core/annotate-data value problems true)]
                          (if (instance? inspectable.core.AnnWrapper ann)
                            (:value ann)
                            ann))))))

(defn value-as-tree-panel [value problems]
  (ss/scrollable
   (make-tree 
    (core/annotate-data value problems true))))


(defn pprint-structure-to-str [x]
  (with-out-str
    ;; we are using the spec printer but any edn printer will work
    ;; just because it's easy to build one
    (pr-spec/pprint x
                    {}
                    (utils/custom-printer
                     {}
                     {:visit-record-fn utils/visit-ann-wrapper}))))

(defn value-as-pp-panel [value problems fail-form-sym]
  (ss/scrollable
   (doto (ss/editor-pane :content-type "text/html"
                         :editable? false
                         :font {:name :monospaced :size 15}
                         :text (str "<pre>"
                                    (pprint-structure-to-str (cond-> (core/annotate-data value problems false)
                                                                     fail-form-sym (conj fail-form-sym)))
                                    "</pre>"))
     (.putClientProperty JEditorPane/HONOR_DISPLAY_PROPERTIES true))))

(defn pretty-explain
  ([ex-data] (pretty-explain nil ex-data))
  ([fail-form-sym #:clojure.spec.alpha{:keys [problems value]}]
   (-> (ss/frame :title "Inspectable"
                 :width 800
                 :height 850
                 :content (ss/border-panel
                           :vgap 30
                           :background (sscolor/color (get-color :header-background))
                           :north (ss/label :text (if fail-form-sym
                                                    (format "<html>Error calling <b>%s</b></html>" fail-form-sym)
                                                    "Spec failed") 
                                            :background (sscolor/color (get-color :header-background))
                                            :font {:name :monospaced :size 15}
                                            :foreground (sscolor/color (get-color :header-foreground)))
                           :center (ss/tabbed-panel :placement :top
                                                    :tabs (cond-> []
                                                            true                (conj {:title "Pretty print"
                                                                                       :content (value-as-pp-panel value problems fail-form-sym)})
                                                            (not fail-form-sym) (conj {:title "Tree"
                                                                                       :content (value-as-tree-panel value problems)})
                                                            fail-form-sym       (conj {:title "Args trees"
                                                                                       :content (value-as-args-tree-panel value problems)})))))
       
       ss/show!)))

