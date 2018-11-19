(set-env!
  :source-paths #{"src"}
  :dependencies '[[adzerk/boot-cljs "2.1.5" :scope "test"]
                  [adzerk/boot-reload "0.6.0" :scope "test"]
                  [org.clojure/test.check "0.9.0" :scope "test"]
                  [nightlight "2.3.2" :scope "test"]
                  ; project deps
                  [org.clojure/clojure "1.9.0"]
                  [org.clojure/clojurescript "1.10.439"]
                  [org.clojure/core.async "0.4.474"]
                  [org.clojure/data.json "0.2.6"]
                  [org.clojure/tools.cli "0.4.1"]
                  [javax.xml.bind/jaxb-api "2.3.0"]
                  [http-kit "2.2.0"]
                  [ring "1.6.2"]
                  [reagent "0.8.1"]
                  [cljs-react-material-ui "0.2.50"]
                  [compojure "1.6.1"]
                  [com.rpl/specter "1.1.2"]
                  [com.taoensso/sente "1.13.1"]
                  [org.clojure/java.jdbc "0.7.8"]
                  [com.h2database/h2 "1.4.196"]])

(task-options!
  pom {:project 'steemobservatory
       :version "1.0.0-SNAPSHOT"
       :description "FIXME: write description"}
  aot {:namespace '#{steemobservatory.server}}
  jar {:main 'steemobservatory.server}
  sift {:include #{#"\.jar$"}})

(require
  '[clojure.spec.test.alpha :refer [instrument]]
  '[nightlight.core :as nightlight]
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  'steemobservatory.server)

(deftask run []
  (set-env! :resource-paths #{"resources" "dev-resources"})
  (comp
    (with-pass-thru _
      (instrument)
      (let [server (steemobservatory.server/-main)
            port (-> server meta :local-port)
            url (str "http://localhost:" port "/index.html")]
        (println "Started app on" url)
        (nightlight/start {:port 4000 :url url})))
    (watch)
    (reload :asset-path "steemobservatory")
    (cljs
      :source-map true
      :optimizations :none
      :compiler-options {:asset-path "main.out"})
    (target)))

(deftask build []
  (set-env! :resource-paths #{"resources" "prod-resources"})
  (comp
    (cljs :optimizations :advanced)
    (aot)
    (pom)
    (uber)
    (jar)
    (sift)
    (target)))

(deftask build-simple []
  (set-env! :resource-paths #{"resources" "prod-resources"})
  (comp
    (cljs :optimizations :advanced)
    (target)))

