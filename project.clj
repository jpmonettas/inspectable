(defproject inspectable "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [seesaw "1.4.5"]
                 [org.clojure/test.check "0.10.0-alpha2"]
                 [pretty-spec "0.1.0-SNAPSHOT"]
                 [fipp "0.6.9"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
