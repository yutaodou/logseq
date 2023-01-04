(ns frontend.db.datascript
  (:require [frontend.db.protocol :as protocol]
            [datascript.core :as d]
            [frontend.db.conn :as conn]))

(defrecord Datascript [repo]
  protocol/DB
  (transact! [this tx-data tx-meta]
    (when-let [conn (conn/get-db repo false)]
      (d/transact! conn tx-data tx-meta)))
  (q [this query inputs]
    (when-let [db (conn/get-db repo)]
      (apply d/q query (cons db inputs))))
  (pull [this selector eid]
    (when-let [db (conn/get-db repo)]
      (d/pull db selector eid)))
  (pull-many [this selector eids]
    (when-let [db (conn/get-db repo)]
      (d/pull-many db selector eids)))
  (entity [this eid]
    (when-let [db (conn/get-db repo)]
      (d/entity db eid))))
