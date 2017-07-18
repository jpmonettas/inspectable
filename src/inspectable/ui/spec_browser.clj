(ns inspectable.ui.spec-browser
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [seesaw.core :as ss])
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

(defn tabs [n]
  (apply str (repeat n "\t")))

(defn format-link [form]
  ;; TODO fix the google thing
  (format "<a data=\"%s\" href=\"http://www.google.com\">%s</a>"
               form
               form ))

(defn format-spec-form
  ([form] (format-spec-form form 0))
  ([form indent-level]
   (if (seq? form)
     (let [[f & args] form]
       (cond
         (#{'clojure.spec.alpha/fspec
            'clojure.spec.alpha/or
            'clojure.spec.alpha/cat
            'clojure.spec.alpha/alt} f)
         (format "(%s\n%s)"
                 (format-spec-form f (inc indent-level))
                 (->> args
                      (partition 2)
                      (map (fn [[p1 p2]] (str (tabs (inc indent-level)) p1 " " (format-spec-form p2 (inc indent-level)))))
                      (str/join "\n")))

         (#{'clojure.spec.alpha/keys} f)
         (format "(%s\n%s)"
                 (format-spec-form f (inc indent-level))
                 (->> args
                      (partition 2)
                      (map (fn [[p1 p2]] (str (tabs (inc indent-level)) p1
                                              " ["
                                              (->> p2
                                                   (map #(format-spec-form % (inc indent-level)))
                                                   (str/join (str "\n" (tabs (+ indent-level 2)))))
                                              "]")))
                      (str/join "\n")))

         (#{'clojure.spec.alpha/and} f)
         (format "(%s\n%s)"
                 (format-spec-form f (inc indent-level))
                 (->> args
                      (map #(format-spec-form % (inc indent-level)))
                      (str/join (str "\n" (tabs (inc indent-level))))))
         
         (#{'clojure.spec.alpha/?
            'clojure.spec.alpha/+
            'clojure.spec.alpha/*
            'clojure.spec.alpha/nilable} f)
         (format "(%s %s)"
                 (format-spec-form f (inc indent-level))
                 (format-spec-form (first args) (inc indent-level)))
          
         true (str form)))
     
     ;;else
     (cond
       (and (or (qualified-keyword? form)
                (qualified-symbol? form))
            (contains? (s/registry) form))
       (format-link form)

       true (str form)))))

(defn str-to-sym-or-key [s]
  (if (.startsWith s ":")
    (keyword (subs s 1))
    (symbol s)))

(declare show-spec-fn)

(defn browser-editor-link-listener [e]
  (when (= (.getEventType e) HyperlinkEvent$EventType/ACTIVATED)
    (let [spec (-> (.getSourceElement e)
                   .getAttributes
                   (.getAttribute HTML$Tag/A)
                   (.getAttribute HTML$Attribute/DATA))]
      (show-spec-fn (str-to-sym-or-key spec)))))

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
    (ss/listen editor :hyperlink browser-editor-link-listener)
    (show-spec-fn spec)
    (-> (ss/frame :title "Spec browser"
                  :content (ss/border-panel
                            :north (ss/scrollable nav-panel)
                            :center (ss/scrollable editor)))
                  ss/pack!
                  ss/show!)))




