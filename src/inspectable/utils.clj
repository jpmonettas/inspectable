(ns inspectable.utils
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [fipp.edn :as fipp-edn :refer [pretty-coll]]
            [fipp.clojure :as fipp-clojure]
            [fipp.ednize :refer [edn record->tagged]]
            [fipp.visit :as fipp-visit :refer [visit visit*]]
            [fipp.engine :refer [pprint-document]]
            [pretty-spec.core :refer [pprint]]
            [inspectable.ui.themes :as themes]))

(defn clean-qualified-symbol [s]
  (symbol
   (str
    (when (qualified-keyword? s) ":")
    (-> (namespace s)
        (str/replace "clojure.core" "core")
        (str/replace "clojure.spec.alpha" "spec"))
    "/"
    (name s))))

;; (defn spec-form-to-str [form]
;;   (walk/postwalk
;;    (fn [v]
;;      (if (or (qualified-keyword? v)
;;              (qualified-symbol? v))
;;        (clean-qualified-symbol v)
;;        v))
;;    form))


(defn map-contains-pred? [pred]
  (and (seq? pred)
       (= (first pred) 'clojure.core/fn)
       (= (-> pred (nth 2) first) 'clojure.core/contains?)))

(defn format-problem [problem]
  (let [pred (:pred problem)]
    (cond
      (map-contains-pred? pred)
      (format "Missing required key <b>%s</b>" (-> pred (nth 2) (nth 2)))

      true (format "<b>%s</b>"  (with-out-str
                                  (pprint pred {}))))))

(defn format-problems [problems]
  (str "<span style=\"background-color: " (themes/get-color :problem-background) ";\">fails for "
       (->> problems
            (map format-problem)
            (str/join " and "))
       "</span>"))

(defn custom-printer [symbols {:keys [visit-keyword-fn visit-record-fn visit-symbol-fn]}]
  (reify

    fipp-visit/IVisitor
    
    (visit-unknown [this x]  (visit this (edn x)))

    (visit-nil [this] [:text "nil"])

    (visit-boolean [this x]
      [:text (str x)])

    (visit-string [this x]
      [:text (pr-str x)])

    (visit-character [this x]
      [:text (pr-str x)])

    (visit-symbol [this x]
      (if visit-symbol-fn
        (visit-symbol-fn this x)
        [:text (str x)]))

    (visit-keyword [this x]
      (if visit-keyword-fn
        (visit-keyword-fn this x)
        [:text (str x)]))
    
    (visit-number [this x]
      [:text (pr-str x)])

    (visit-seq [this x]
      (if-let [pretty (symbols (first x))]
        (pretty this x)
        (pretty-coll this "(" x :line ")" visit)))

    (visit-vector [this x]
      (pretty-coll this "[" x :line "]" visit))

    (visit-map [this x]
      (pretty-coll this "{" x [:span "," :line] "}"
                   (fn [printer [k v]]
                     [:span (visit printer k) " " (visit printer v)])))

    (visit-set [this x]
      (pretty-coll this "#{" x :line "}" visit))

    (visit-tagged [this {:keys [tag form]}]
      [:group "#" (pr-str tag)
       (visit this form)])

    (visit-meta [this m x]
      (visit* this x))

    (visit-var [this x]
      [:text (str x)])

    (visit-pattern [this x]
      [:text (pr-str x)])

    (visit-record [this x]
      (if visit-record-fn
        (visit-record-fn this x)
        (visit this (record->tagged x))))))


(defn visit-ann-wrapper [printer record]
  (if (instance? inspectable.core.AnnWrapper record)
    (if-let [problems (-> record :data :node-problems)]
      [:span
       [:pass (str "<span style=\"color: " (themes/get-color :problem-foreground) ";\">")]
       (visit printer (:value record))
       [:pass (str "</span>&nbsp;<span style=\"background-color: " (themes/get-color :problem-background) ";\">"
                   ";; fails for "
                   (->> problems
                        (map format-problem)
                        (str/join " and "))
                   "</span>")]]
      (visit printer (:value record)))
    (visit printer (record->tagged record))))


