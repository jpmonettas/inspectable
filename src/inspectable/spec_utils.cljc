(ns inspectable.spec-utils
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.string :as str]
            [clojure.walk :as walk]))

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
                  (let [checkable-part (if (str/starts-with? spec-symbol-str ":")
                                        (subs spec-symbol-str 1)
                                        spec-symbol-str)]
                   (re-find (re-pattern filter-regex) checkable-part))))
              sorted-specs)
      sorted-specs)))

#?(:clj
   (defn- get-multi-spec-sub-specs
     "Given a multi-spec form, call its multi method methods to retrieve
  its subspecs."
     [multi-spec-form]
     (let [[_ multi-method-symbol & _] multi-spec-form]
       (->> (resolve multi-method-symbol)
            deref
            methods 
            (map (fn [[spec-k method]]
                   [spec-k (s/form (method nil))]))))))

#?(:clj
   (defn- add-multi-specs
     "Walk down a spec form and for every subform that is a multi-spec
  add its sub specs."
     [form]
     (walk/postwalk (fn [sub-form]
                      (if (and (coll? sub-form)
                               (symbol? (first sub-form))
                               (-> sub-form first name (= "multi-spec")))
                        (concat sub-form (get-multi-spec-sub-specs sub-form))
                        sub-form))
                    form)))

(defn spec-form
  "Return spec form enhanced if it contains multispecs"
  [spec-name]
  #?(:clj (try
            (-> (s/form spec-name)
                (add-multi-specs))
            (catch Exception e nil))
     :cljs (try
             (s/form spec-name)
             (catch js/Error e nil))))

(defn spec-sample
  "Return a sample from a spec"
  [spec-name]
  (-> (s/get-spec spec-name)
      (s/gen)
      (sgen/generate)))
