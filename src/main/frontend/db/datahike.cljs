(ns frontend.db.datahike
  (:require [frontend.db.protocol :as protocol]
            [electron.ipc :as ipc]
            [logseq.graph-parser.util :as gp-util]))

(defrecord Datahike [repo]
  protocol/DB
  (transact! [this tx-data tx-meta]
    (let [tx-meta (gp-util/remove-nils tx-meta)
          opt (cond-> {:tx-data tx-data}
                (seq tx-meta)
                (assoc :tx-meta tx-meta))
          opt-str (pr-str opt)]
      (ipc/ipc :db/transact repo opt-str)))
  (q [this query inputs]
    (ipc/ipc :db/query repo :query inputs))
  (pull [this selector eid]
    (ipc/ipc :db/query repo :pull selector eid))
  (pull-many [this selector eids]
    (ipc/ipc :db/query repo :pull-many selector eids))
  (entity [this eid]
    (ipc/ipc :db/query repo :entity eid)))
