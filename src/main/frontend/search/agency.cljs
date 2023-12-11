(ns frontend.search.agency
  "Agent entry for search engine impls"
  (:require [frontend.search.protocol :as protocol]
            [frontend.search.browser :as search-browser]
            [frontend.search.plugin :as search-plugin]
            [frontend.state :as state]))

(defn get-registered-engines
  [repo]
  [(search-browser/->Browser repo)
   (when state/lsp-enabled?
     (for [s (state/get-all-plugin-services-with-type :search)]
       (search-plugin/->Plugin s repo)))])

(defn- get-flatten-registered-engines
  [repo]
  (->> (flatten (get-registered-engines repo))
       (remove nil?)))

(deftype Agency [repo]
  protocol/Engine

  (query [_this q opts]
    ;; (println "D:Search > Query blocks:" repo q opts)
    (let [[e1 e2] (get-registered-engines repo)]
      (doseq [e e2]
        (protocol/query e q opts))
      (protocol/query e1 q opts)))

  (rebuild-blocks-indice! [_this]
    (println "D:Search > Initial blocks indice!:" repo)
    (let [[e1 e2] (get-registered-engines repo)]
      (doseq [e e2]
        (protocol/rebuild-blocks-indice! e))
      (protocol/rebuild-blocks-indice! e1)))

  (transact-blocks! [_this data]
    (doseq [e (get-flatten-registered-engines repo)]
      (protocol/transact-blocks! e data)))

  (truncate-blocks! [_this]
    (println "D:Search > Truncate blocks!" repo)
    (doseq [e (get-flatten-registered-engines repo)]
      (protocol/truncate-blocks! e)))

  (remove-db! [_this]
    (println "D:Search > Remove Db!" repo)
    (doseq [e (get-flatten-registered-engines repo)]
      (protocol/remove-db! e))))
