(ns electron.db
  (:require ["@logseq/datahike" :as datahike]
            [electron.utils :as utils]
            [electron.db-schema :as schema]
            ["path" :as path]
            [clojure.pprint :as pprint]
            [cljs-bean.core :as bean]))

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
  (let [config (get-db-config repo)]
    (when-not (.databaseExists datahike config)
      (create-database! repo))
    (.transact datahike config (pr-str tx-data-meta))))

(defn query
  [repo kind args]
  (let [config (get-db-config repo)]
    (case kind
      :entity (datahike/entity config (first args))
      :pull   (apply datahike/pull config args)
      :pull-many (apply datahike/pull-many config args)
      :query (apply datahike/query config args))))

(comment
  (def repo "logseq_local_/Users/tiensonqin/Desktop/db-demo")

  (def config (get-db-config repo))

  (datahike/query
    (str
     '[:find ?n
       :where
       [?e :block/page ?p]
       [?p :block/name ?n]])
    (bean/->js [["db", config]]))
  )
