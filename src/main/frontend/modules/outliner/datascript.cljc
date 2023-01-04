(ns frontend.modules.outliner.datascript
  #?(:clj (:require [clojure.core :as core]))
  #?(:cljs (:require-macros [frontend.modules.outliner.datascript]))
  #?(:cljs (:require [frontend.db.conn :as conn]
                     [frontend.db :as db]
                     [frontend.modules.outliner.pipeline :as pipelines]
                     [frontend.modules.editor.undo-redo :as undo-redo]
                     [frontend.state :as state]
                     [frontend.config :as config]
                     [logseq.graph-parser.util :as gp-util]
                     [lambdaisland.glogi :as log]
                     [frontend.search :as search])))

#?(:cljs
   (defn new-outliner-txs-state [] (atom [])))

#?(:cljs
   (defn outliner-txs-state?
     [state]
     (and
       (instance? cljs.core/Atom state)
       (coll? @state))))

#?(:cljs
   (defn after-transact-pipelines
     [repo {:keys [tx-meta] :as tx-report}]
     (when-not config/test?
       (pipelines/invoke-hooks tx-report)

       (when (:outliner/transact? tx-meta)
         (undo-redo/listen-outliner-operation tx-report))

       (search/sync-search-indice! repo tx-report))))

#?(:cljs
   (defn- remove-nil-from-transaction
     [txs]
     (some->> (gp-util/remove-nils txs)
              (map (fn [x]
                     (if (map? x)
                       (update-vals x (fn [v]
                                        (if (vector? v)
                                          (remove nil? v)
                                          v)))
                       x))))))

#?(:cljs
   (defn transact!
     [txs opts]
     (let [txs (remove-nil-from-transaction txs)
           txs (map (fn [m] (if (map? m)
                              (dissoc m
                                      :block/children :block/meta :block/top? :block/bottom? :block/anchor
                                      :block/title :block/body :block/level :block/container :db/other-tx)
                              m)) txs)]
       (when (and (seq txs)
                  (not (:skip-transact? opts))
                  (not (contains? (:file/unlinked-dirs @state/state)
                                  (config/get-repo-dir (state/get-current-repo)))))
         ;; (frontend.util/pprint txs)
         (try
           (let [repo (get opts :repo (state/get-current-repo))
                 conn (conn/get-db repo false)
                 editor-cursor (state/get-current-edit-block-and-position)
                 meta (merge opts {:editor-cursor editor-cursor})]
             (db/transact! repo txs (assoc meta :outliner/transact? true)))
           (catch :default e
             (log/error :exception e)
             (throw e)))))))
