(ns frontend.db.core
  (:require [frontend.db.protocol :as protocol]
            [frontend.db.datahike :as datahike]
            [frontend.db.datascript :as datascript]
            [frontend.util :as util]))

(defn get-db
  [repo]
  (if (util/electron?)
    (datahike/->Datahike repo)
    (datascript/->Datascript repo)))

(defn transact!
  ([repo tx-data]
   (transact! repo tx-data nil))
  ([repo tx-data tx-meta]
   (protocol/transact! (get-db repo) tx-data tx-meta)))

(defn q
  [repo query & inputs]
  (protocol/q (get-db repo) query inputs))

(defn entity
  [repo eid]
  (protocol/entity (get-db repo) eid))

(defn pull
  [repo selector eid]
  (protocol/pull (get-db repo) selector eid))

(defn pull-many
  [repo selector eids]
  (protocol/pull-many (get-db repo) selector eids))

(defn datoms
  [repo index & col]
  (apply protocol/datoms (get-db repo) repo index col))
