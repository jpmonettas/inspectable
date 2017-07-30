(ns inspectable.server-client
  (:require [clojure.core.async :as async]
            [cognitect.transit :as t]
            [inspectable.ui.fail-inspector :as fail-inspector]
            [inspectable.ui.spec-browser :as spec-browser]
            [org.httpkit.server :as server]
            [ring.middleware.cors :refer [wrap-cors]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           java.util.UUID))

(defonce server (atom nil))
(defonce server-client-responses (atom {}))
(defonce socket-ch (atom nil))

(defn- read-transit [s]
  (t/read (t/reader (ByteArrayInputStream. (.getBytes s)) :json)))

(defn- to-transit-str [o]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)
        _ (t/write writer o)]
    (.toString out)))

(defn- server-client-request [event data]
  (let [k (str (UUID/randomUUID))
        p (promise)]
    (swap! server-client-responses assoc k p)
    (server/send! @socket-ch (to-transit-str [event k data]))
    (deref p 3000 nil)))

(defn- server-client-spec-list [filter-regex]
  (server-client-request :spec-list filter-regex))

(defn- server-client-spec-form [spec]
  (server-client-request :spec-form spec))

(defn- server-handler [req]
  (if (= (:uri req) "/socket")
    (server/with-channel req ch
      (reset! socket-ch ch)
      (server/on-close ch (fn [status] (println "ch closed: " status)))
      (server/on-receive ch (fn [msg]
                              (async/go
                               (let [[event & r] (read-transit msg)] 
                                 (cond
                                   (= event :pretty-explain) (let [data (first r)]
                                                               (fail-inspector/pretty-explain (:fn-sym data)
                                                                                              (:ex-data data)))
                                   (= event :browse-spec) (let [data (first r)]
                                                            (spec-browser/browse-spec (:spec data)
                                                                                      server-client-spec-list                     
                                                                                      server-client-spec-form))
                                   (= event :response) (let [[k data] r]
                                                         (deliver (get @server-client-responses k) data))))))))
    {:status 404}))

(defn start-cljs []
  (reset! server 
          (server/run-server (-> #'server-handler
                                 (wrap-cors :access-control-allow-origin [#".*"]
                                            :access-control-allow-methods [:post]))
                             {:port 53427})))

(defn stop-cljs []
  (@server))
