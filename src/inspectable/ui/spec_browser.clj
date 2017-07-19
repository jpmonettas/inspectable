(ns inspectable.ui.spec-browser
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [seesaw.core :as ss]
            [clojure.pprint :as pp])
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

(defn clean-qualified-symbol [s]
  (symbol
   (str
    (when (qualified-keyword? s) ":")
    (-> (namespace s)
        (str/replace "clojure.core" "core")
        (str/replace "clojure.spec.alpha" "spec"))
    "/"
    (name s))))

(defn spec-form-to-str [form]
  (walk/postwalk
   (fn [v]
     (if (or (qualified-keyword? v)
             (qualified-symbol? v))
       (clean-qualified-symbol v)
       v))
   form))


(defn spec-pp-dispatch [form]
  (if (seq? form)
    (let [[f & args] form]
      (cond
        (#{'clojure.spec.alpha/fspec
           'clojure.spec.alpha/or
           'clojure.spec.alpha/cat
           'clojure.spec.alpha/alt} f)
        (pp/pprint-logical-block
         :prefix "(" :suffix ")"
         (pp/pprint-indent :block 1)
         (pp/write-out f)    
         (.write ^java.io.Writer *out* " ")
         (pp/pprint-newline :linear)
         (pp/print-length-loop [[[p1 p2 ] & r] (partition 2 args)]
                               (when p1
                                 (pp/write-out p1)
                                 (.write ^java.io.Writer *out* " ")
                                 (pp/write-out p2)
                                 (when r
                                   (.write ^java.io.Writer *out* " ")
                                   (pp/pprint-newline :linear))
                                 (recur r))))

        (#{'clojure.spec.alpha/and
           'clojure.spec.alpha/merge} f)
        (pp/pprint-logical-block
         :prefix "(" :suffix ")"
         (pp/pprint-indent :block 1)
         (pp/write-out f)    
         (.write ^java.io.Writer *out* " ")
         (pp/pprint-newline :linear)
         (pp/print-length-loop [[p & r] args]
                               (when p
                                 (pp/write-out p)
                                 (when r
                                   (.write ^java.io.Writer *out* " ")
                                   (pp/pprint-newline :linear))
                                 (recur r))))

        (#{'clojure.spec.alpha/keys} f)
        (pp/pprint-logical-block
         :prefix "(" :suffix ")"
         (pp/pprint-indent :block 1)
         (pp/write-out f)    
         (.write ^java.io.Writer *out* " ")
         (pp/pprint-newline :linear)
         (pp/print-length-loop [[[kt ks] & r] (partition 2 args)]
                               (when kt
                                 (pp/write-out kt)
                                 (.write ^java.io.Writer *out* " ")
                                 (pp/pprint-logical-block
                                  :prefix "[" :suffix "]"
                                  (pp/print-length-loop [[k & rk] ks]
                                                        (when k
                                                          (pp/write-out k)
                                                          (when rk
                                                            (.write ^java.io.Writer *out* " ")
                                                            (pp/pprint-newline :linear))
                                                          (recur rk))))
                                 (when r
                                   (pp/pprint-newline :mandatory))
                                 (recur r))))

         (#{'clojure.spec.alpha/?
            'clojure.spec.alpha/+
            'clojure.spec.alpha/*
            'clojure.spec.alpha/nilable} f)
         (pp/pprint-logical-block
         :prefix "(" :suffix ")"
         (pp/pprint-indent :block 1)
         (pp/write-out f)    
         (.write ^java.io.Writer *out* " ")
         (pp/write-out (first args)))
         
         (#{'clojure.spec.alpha/multi-spec} f)
         (let [[mm retag & multi-specs] args]
          (pp/pprint-logical-block
           :prefix "(" :suffix ")"
           (pp/pprint-indent :block 1)
           (pp/write-out f)    
           (.write ^java.io.Writer *out* " ")
           (pp/pprint-newline :linear)
           (pp/write-out mm)
           (.write ^java.io.Writer *out* " ")
           (pp/pprint-newline :linear)
           (pp/write-out retag)
           (pp/pprint-newline :mandatory)
           (pp/print-length-loop [[[k s] & r] multi-specs]
                                 (when k
                                   (pp/write-out k)
                                   (.write ^java.io.Writer *out* " ")
                                   (pp/write-out s)
                                   (when r
                                     (pp/pprint-newline :mandatory))
                                   (recur r)))))
         
        true (pr form)))
    
    ;;else
    (cond
      (and (or (qualified-keyword? form)
               (qualified-symbol? form))
           (contains? (s/registry) form))
      (.write ^java.io.Writer *out* (format-link form))

      (and (or (qualified-keyword? form)
               (qualified-symbol? form)))
      (pr (clean-qualified-symbol form))
      
      true (pr form))))

(defn format-spec-form [form]
  (clojure.pprint/write form
                        :stream nil
                        :dispatch spec-pp-dispatch))

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
    (-> (ss/frame :title "Spec browser"
                  :content (ss/border-panel
                            :north (ss/scrollable nav-panel)
                            :center (ss/scrollable editor)))
                  ss/pack!
                  ss/show!)))




