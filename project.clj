(defproject inspectable "0.1.1"
  :description "Tools for clojure.spec"
  :url "https://github.com/jpmonettas/inspectable"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [seesaw "1.4.5"]
                 [pretty-spec "0.1.0"]
                 [fipp "0.6.9"]

                 ;; for the cljs part
                 [org.clojure/clojurescript "1.9.671" :scope "provided"]
                 [org.clojure/test.check "0.9.0"]
                 [cljs-ajax "0.6.0"]
                 [http-kit "2.2.0"]
                 [ring-cors "0.1.11"]
                 [ring-middleware-format "0.7.2"]]
  :target-path "target/%s"

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src"]
     :figwheel     true
     :compiler     {:main                 inspectable.cljs-repl
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true}}]}
  
  :profiles {:uberjar {:aot :all}
             :dev {:repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :dependencies [[figwheel-sidecar "0.5.11"]
                                  [com.cemerick/piggieback "0.2.2"]]}})
