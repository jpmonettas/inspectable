(ns inspectable.ui.spec-browser
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [seesaw.core :as ss]
            [seesaw.swingx :as ssx]
            [clojure.pprint :as pp]
            [pretty-spec.core :as pr-spec]
            [inspectable.utils :as utils]
            [fipp.visit :refer [visit]]
            [fipp.edn :as fipp-edn]
            [inspectable.ui.themes :as themes]
            [inspectable.spec-utils :as spec-utils])
  (:import javax.swing.event.HyperlinkEvent$EventType
           [javax.swing.text.html HTML$Attribute HTML$Tag]
           javax.swing.JEditorPane
           [java.awt Dimension]))




(defn build-link [form]
  [:span
   (format "<a data=\"%s\" href=\"http://\" style=\"color:%s;text-decoration:none;font-weight:bold;\">"
           form
           (themes/get-color :browser-link-foreground))
     (str form)
     "</a>"])

(defn visit-keyword-fn [get-spec-form printer form]
  (if (and (qualified-keyword? form)
           (get-spec-form form))
    (build-link form)
    [:text (str form)]))

(defn visit-symbol-fn [get-spec-form printer form]
  (cond
    (and (qualified-symbol? form)
         (get-spec-form form))
    (build-link form)
    
    (qualified-symbol? form)
    (str (utils/clean-qualified-symbol form))
    
    true [:text (str form)]))

(defn visit-multispec [printer [f & args :as form]]
  (let [[mm retag & multi-specs] args]
    [:group "("
           [:align (visit printer f) :line (visit printer mm) :line (visit printer retag) [:break]  
      (when (seq multi-specs)
        (->> multi-specs
             (map (fn [[k v]]
                    [:span (visit printer k) " " (visit printer v)]))
             (interpose :line)))
      ")"]]))


(defn format-spec-form [form get-spec-form]
  (with-out-str (pr-spec/pprint form
                                {}
                                (utils/custom-printer
                                 (merge (pr-spec/build-symbol-map {visit-multispec '[multi-spec]})
                                        pr-spec/default-symbols
                                        fipp.clojure/default-symbols)
                                 {:visit-keyword-fn (partial visit-keyword-fn get-spec-form)
                                  :visit-symbol-fn (partial visit-symbol-fn get-spec-form)}))))

(defn str-to-sym-or-key [s]
  (if (.startsWith s ":")
    (keyword (subs s 1))
    (symbol s)))


(defn browse-spec
  [spec spec-list spec-form spec-sample]
  (let [spec-form-editor (doto (ss/editor-pane :content-type "text/html"
                                               :editable? false
                                               :minimum-size (Dimension. 400 400)
                                               :font {:name :monospaced :size 15})
                           (.putClientProperty JEditorPane/HONOR_DISPLAY_PROPERTIES true))
        spec-sample-editor (doto (ss/editor-pane :editable? false
                                                 :minimum-size (Dimension. 400 400)
                                                 :font {:name :monospaced :size 15})
                             (.putClientProperty JEditorPane/HONOR_DISPLAY_PROPERTIES true))
        nav-panel (ss/horizontal-panel)
        nav-stack (atom (list))
        nav-click-fn (fn [spec _]
                       (swap! nav-stack
                              #(drop-while (partial not= spec) %)))
        show-spec-fn (fn [spec-name]
                       (swap! nav-stack conj spec-name))
        frame (doto (ss/frame :title "Inspectable browser"
                              :content (ss/border-panel
                                        :north (ss/scrollable nav-panel) 
                                        :center (ss/left-right-split (ssx/titled-panel :title "Spec"
                                                                                       :content (ss/scrollable spec-form-editor))
                                                                     (ssx/titled-panel :title "Sample"
                                                                                       :content (ss/scrollable spec-sample-editor))))
                              :width 800
                              :height 850))]
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
                 (ss/config! spec-form-editor :text
                             (format "<html><body>%s</body></html>"
                                     (if (string? head)
                                       (format "<ul style=\"list-style-type: none\">%s</ul>"
                                               (->> (spec-list head)
                                                    (map #(str "<li>" (format-spec-form % spec-form) "</li>"))
                                                    str/join))
                                       (format "<pre>%s</pre>" (format-spec-form (spec-form head) spec-form)))))
                 (when-not (string? head)
                  (ss/config! spec-sample-editor :text "Generating sample...")
                  (ss/invoke-later (ss/config! spec-sample-editor
                                               :text (try
                                                       (with-out-str (fipp-edn/pprint (spec-sample head)))
                                                       (catch Exception e (.getMessage e))))))))
    (ss/listen spec-form-editor :hyperlink (fn [e]
                                   (when (= (.getEventType e) HyperlinkEvent$EventType/ACTIVATED)
                                     (let [spec (-> (.getSourceElement e)
                                                    .getAttributes
                                                    (.getAttribute HTML$Tag/A)
                                                    (.getAttribute HTML$Attribute/DATA))]
                                       (show-spec-fn (str-to-sym-or-key spec))))))
    (show-spec-fn spec)
    (-> frame 
        ss/show!)))




