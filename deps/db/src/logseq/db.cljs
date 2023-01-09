(ns logseq.db
  "Main namespace for public db fns"
  (:require [logseq.db.default :as default-db]
            [logseq.db.schema :as db-schema]
            [datascript.core :as d]))

(defn start-conn
  "Create datascript conn with schema and default data"
  ([]
   (start-conn nil))
  ([transact-fn]
   (let [tx-data (concat
                  [{:schema/version db-schema/version}
                   {:block/name "card"
                    :block/original-name "card"
                    :block/uuid (d/squuid)}]
                  default-db/built-in-pages)]
     (if transact-fn
       (do
         (transact-fn tx-data)
         (atom :logseq/db))
       (let [db-conn (d/create-conn db-schema/schema)]
         (d/transact! db-conn tx-data)
         db-conn)))))
