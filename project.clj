(defproject inspectable "0.1.0"
  :description "Tools for clojure.spec"
  :url "https://github.com/jpmonettas/inspectable"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [seesaw "1.4.5"]
                 [pretty-spec "0.1.0"]
                 [fipp "0.6.9"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
