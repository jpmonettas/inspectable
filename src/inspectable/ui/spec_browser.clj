(ns inspectable.ui.spec-browser
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [seesaw.core :as ss]
            [clojure.pprint :as pp]
            [pretty-spec.core :as pr-spec]
            [inspectable.utils :as utils]
            [fipp.visit :refer [visit]])
  (:import javax.swing.event.HyperlinkEvent$EventType
           [javax.swing.text.html HTML$Attribute HTML$Tag]
           javax.swing.JEditorPane))

(defn spec-list
  "Retrieves a list of all specs in the registry, sorted by ns/name.
  If filter-regex is not empty, keep only the specs with that prefix."
  [filter-regex]
  (let [sorted-specs (->> (s/registry)
                          keys
                          (sort-by str))]
    (if (not-empty filter-regex)
      (filter (fn [spec-symbol]
                (let [spec-symbol-str (str spec-symbol)]
                 (let [checkable-part (if (.startsWith ^String spec-symbol-str ":")
                                        (subs spec-symbol-str 1)
                                        spec-symbol-str)]
                   (re-find (re-pattern filter-regex) checkable-part))))
              sorted-specs)
      sorted-specs)))

(defn get-multi-spec-sub-specs
  "Given a multi-spec form, call its multi method methods to retrieve
  its subspecs."
  [multi-spec-form]
  (let [[_ multi-method-symbol & _] multi-spec-form]
   (->> @(resolve multi-method-symbol)
        methods 
        (map (fn [[spec-k method]]
               [spec-k (s/form (method nil))])))))

(defn add-multi-specs
  "Walk down a spec form and for every subform that is a multi-spec
  add its sub specs."
  [form]
  (walk/postwalk (fn [sub-form]
                   (if (and (coll? sub-form)
                            (symbol? (first sub-form))
                            (-> sub-form first name (= "multi-spec")))
                     (concat sub-form (get-multi-spec-sub-specs sub-form))
                     sub-form))
                 form))

(defn spec-form
  "Return spec form enhanced if it contains multispecs"
  [spec-name]
  (-> (s/form spec-name)
      add-multi-specs))


(defn build-link [form]
  [:span
     [:pass (format "<a data=\"%s\" href=\"http://\">" form)]
     (str form)
     [:pass "</a>"]])

(defn visit-keyword-fn [printer form]
  (if (and (qualified-keyword? form)
           (contains? (s/registry) form))
    (build-link form)
    [:text (str form)]))

(defn visit-symbol-fn [printer form]
  (cond
    (and (qualified-symbol? form)
         (contains? (s/registry) form))
    (build-link form)
    
    (qualified-symbol? form)
    (str (utils/clean-qualified-symbol form))
    
    true [:text (str form)]))

(defn visit-multispec [printer [f & args :as form]]
  (let [[mm retag & multi-specs] args]
    [:group "("
     [:align (visit printer f) :line mm :line retag [:break]  
      (when (seq multi-specs)
        (->> multi-specs
             (map (fn [[k v]]
                    [:span (visit printer k) " " (visit printer v)]))
             (interpose :line)))
      ")"]]))

(defn format-spec-form [form]
  (with-out-str (pr-spec/pprint form
                                {}
                                (utils/custom-printer
                                 (merge (pr-spec/build-symbol-map {visit-multispec '[multi-spec]})
                                        pr-spec/default-symbols
                                        fipp.clojure/default-symbols)
                                 {:visit-keyword-fn visit-keyword-fn
                                  :visit-symbol-fn visit-symbol-fn}))))

(defn str-to-sym-or-key [s]
  (if (.startsWith s ":")
    (keyword (subs s 1))
    (symbol s)))


(defn browse-spec [spec]
  (let [editor (doto (ss/editor-pane :content-type "text/html"
                                :editable? false
                                :font {:name :monospaced :size 15})
                 (.putClientProperty JEditorPane/HONOR_DISPLAY_PROPERTIES true))
        nav-panel (ss/horizontal-panel)
        nav-stack (atom (list))
        nav-click-fn (fn [spec _]
                       (swap! nav-stack
                              #(drop-while (partial not= spec) %)))
        show-spec-fn (fn [spec-name]
                       (swap! nav-stack conj spec-name))]

    (add-watch nav-stack :watcher
               (fn [_ _ _ [head & _ :as new-stack]]
                 (ss/config! nav-panel
                             :items (->> new-stack
                                         (map #(ss/button :text (if (string? %)
                                                                  (str "All " %)
                                                                  (str %))
                                                          :listen [:action (partial nav-click-fn %)]))
                                         (cons (ss/make-widget [:fill-v 55]))
                                         reverse))
                 (ss/config! editor :text
                             (format "<html><body>%s</body></html>"
                                     (if (string? head)
                                       (format "<ul style=\"list-style-type: none\">%s</ul>"
                                               (->> (spec-list head)
                                                    (map #(str "<li>" (format-spec-form %) "</li>"))
                                                    str/join))
                                       (format "<pre>%s</pre>" (format-spec-form (spec-form head))))))))
    (ss/listen editor :hyperlink (fn [e]
                                   (when (= (.getEventType e) HyperlinkEvent$EventType/ACTIVATED)
                                     (let [spec (-> (.getSourceElement e)
                                                    .getAttributes
                                                    (.getAttribute HTML$Tag/A)
                                                    (.getAttribute HTML$Attribute/DATA))]
                                       (show-spec-fn (str-to-sym-or-key spec))))))
    (show-spec-fn spec)
    (-> (ss/frame :title "Inspectable browser"
                  :content (ss/border-panel
                            :north (ss/scrollable nav-panel)
                            :center (ss/scrollable editor))
                  :width 800
                  :height 850) 
                  ss/show!)))




