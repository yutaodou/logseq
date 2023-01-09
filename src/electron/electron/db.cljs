(ns electron.db
  (:require ["@logseq/datahike" :as datahike]
            [electron.utils :as utils]
            [electron.db-schema :as schema]
            ["path" :as path]
            [clojure.pprint :as pprint]
            [cljs-bean.core :as bean]
            [clojure.edn :as edn]))

(.init datahike)

(defn get-db-config
  [repo]
  (when repo
    (let [dir (.join path (utils/get-graph-dir repo) "logseq" "db")]
      (pr-str {:store {:backend :file :path dir} :schema-flexibility :read}))))

(defn transact-schema!
  [repo]
  (let [config (get-db-config repo)]
    (.transact datahike config (pr-str schema/schema))))

(defn create-database!
  [repo]
  (let [config (get-db-config repo)]
    (when-not (.databaseExists datahike config)
      (.createDatabase datahike config)
      (transact-schema! repo))))

(defn delete-database!
  [repo]
  (let [config (get-db-config repo)]
    (when (.databaseExists datahike config)
      (.deleteDatabase datahike config))))

(defn transact!
  [repo tx-data-meta]
  (let [config (get-db-config repo)
        exists? (.databaseExists datahike config)]
    (if exists?
      (.transact datahike config (pr-str tx-data-meta))
      (create-database! repo))))

(defn query
  [repo kind args-str]
  (let [config (get-db-config repo)
        query? (= kind :query)
        args (->> (edn/read-string args-str)
                  (map (fn [arg]
                         (cond
                           (and query? (= arg :logseq/db)) [["db" config]]
                           (coll? arg) (pr-str arg)
                           :else arg))))]
    (let [f (case kind
              :entity datahike/entity
              :pull datahike/pull
              :pull-many datahike/pullMany
              :query datahike/query
              :datoms datahike/datoms)]
      (when-let [result (apply f config args)]
        (edn/read-string result)))))

(comment
  (def repo "logseq_local_/Users/tiensonqin/Desktop/logseq-graphs/db-3")

  (def config (get-db-config repo))

  (datahike/query
    (str
     '[:find ?n
       :where
       [?p :block/name ?n]])
    (bean/->js [["db", config]]))

  (def tx-data
    '([:db/retract 19 :block/properties-text-values]
      [:db/retract 19 :block/alias]
      [:db/retract 19 :block/warning]
      [:db/retract 19 :block/pre-block?]
      [:db/retract 19 :block/priority]
      [:db/retract 19 :block/invalid-properties]
      [:db/retract 19 :block/properties]
      [:db/retract 19 :block/updated-at]
      [:db/retract 19 :block/repeated?]
      [:db/retract 19 :block/refs]
      [:db/retract 19 :block/scheduled]
      [:db/retract 19 :block/properties-order]
      [:db/retract 19 :block/created-at]
      [:db/retract 19 :block/deadline]
      [:db/retract 19 :block/tags]
      [:db/retract 19 :block/path-refs]
      [:db/retract 19 :block/type]
      [:db/retract 19 :block/marker]
      {:db/id 18, :block/updated-at 1672928886369}
      {:block/uuid #uuid "63b69aea-33ee-45e9-acb1-83cdca2b9ed9",
       :block/properties {},
       :block/journal? true,
       :block/left {:db/id 18},
       :block/refs (),
       :block/journal-day 20230105,
       :block/format :markdown,
       :block/tags (),
       :block/content "nice",
       :db/id 19,
       :block/macros (),
       :block/path-refs (),
       :block/parent {:db/id 18},
       :block/unordered true,
       :block/page {:db/id 18}}))
  )
