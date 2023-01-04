(ns frontend.db.protocol)

(defprotocol DB
  (transact! [this tx-data tx-meta])
  (q [this query inputs])
  (pull [this selector eid])
  (pull-many [this selector eids])
  (entity [this eid]))
