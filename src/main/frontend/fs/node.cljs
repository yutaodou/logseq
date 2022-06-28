(ns frontend.fs.node
  (:require [clojure.string :as string]
            [electron.ipc :as ipc]
            [frontend.config :as config]
            [frontend.db :as db]
            [frontend.fs.protocol :as protocol]
            [frontend.state :as state]
            [frontend.util :as util]
            [goog.object :as gobj]
            [lambdaisland.glogi :as log]
            [promesa.core :as p]
            [frontend.encrypt :as encrypt]
            [frontend.modules.crdt.yjs :as crdt-yjs]))

(defn concat-path
  [dir path]
  (cond
    (nil? path)
    dir

    (string/starts-with? path dir)
    path

    :else
    (str (string/replace dir #"/$" "")
         (when path
           (str "/" (string/replace path #"^/" ""))))))

(defn- contents-matched?
  [disk-content db-content]
  (when (and (string? disk-content) (string? db-content))
    (if (encrypt/encrypted-db? (state/get-current-repo))
      (p/let [decrypted-content (encrypt/decrypt disk-content)]
        (= (string/trim decrypted-content) (string/trim db-content)))
      (p/resolved (= (string/trim disk-content) (string/trim db-content))))))

(defn- write-file-impl!
  [this repo dir path content {:keys [ok-handler error-handler old-content skip-compare?] :as opts} stat]
  (if skip-compare?
    (p/catch
        (p/let [result (ipc/ipc "writeFile" repo path content)]
          (when ok-handler
            (ok-handler repo path result)))
        (fn [error]
          (if error-handler
            (error-handler error)
            (log/error :write-file-failed error))))

    (p/let [disk-content (when (not= stat :not-found)
                           (-> (protocol/read-file this dir path nil)
                               (p/catch (fn [error]
                                          (js/console.error error)
                                          nil))))
            disk-content (or disk-content "")
            disk-content (if (encrypt/encrypted-db? (state/get-current-repo))
                           (encrypt/decrypt disk-content)
                           disk-content)
            db-content (or old-content (db/get-file repo path) "")
            contents-matched? (contents-matched? disk-content db-content)
            edn? (= "edn" (util/get-file-ext path))]
      (when-not contents-matched?
        (ipc/ipc "backupDbFile" (config/get-local-dir repo) path disk-content content))
      (->
       (p/let [ydoc-path (str dir "/logseq/ydoc/"
                              (->
                               path
                               (string/replace (config/get-local-dir repo) "")
                               (string/replace #"^/" "")
                               (string/replace "/" "_"))
                              ".ydoc")
               disk-ydoc (-> (protocol/read-file this dir ydoc-path {:binary true})
                             (p/catch (fn [error]
                                        (prn "Error: " error)
                                        nil)))
               deltas (let [disk-ydoc-content (when disk-ydoc (crdt-yjs/ydoc-binary->text disk-ydoc))
                            equal? (= disk-ydoc-content content)]
                        (when-not equal?
                          (crdt-yjs/get-ytext-deltas db-content content)))
               merged-doc (when-not edn?
                            (if (:by-journal-template? opts)
                              (crdt-yjs/merge-template-doc! path deltas)
                              (crdt-yjs/merge-docs! path disk-ydoc deltas)))
               merged-content (if edn? content (crdt-yjs/get-doc-text merged-doc))
               _ (when merged-doc (ipc/ipc "writeFile" repo ydoc-path (gobj/get (crdt-yjs/serialize merged-doc) "buffer")))
               result (ipc/ipc "writeFile" repo path merged-content)
               mtime (gobj/get result "mtime")]
         (db/set-file-last-modified-at! repo path mtime)
         (db/set-file-content! repo path merged-content)

         (when (or (and merged-doc (not= merged-content content))
                   (not merged-doc))
           (state/pub-event! [:graph/reset-file repo path merged-content opts]))

         (when ok-handler
           (ok-handler repo path result))
         result)
       (p/catch (fn [error]
                  (js/console.error error)
                  (if error-handler
                    (error-handler error)
                    (log/error :write-file-failed error))))))))

(defn- open-dir []
  (p/let [dir-path (util/mocked-open-dir-path)
          result (if dir-path
                   (ipc/ipc "getFiles" dir-path)
                   (ipc/ipc "openDir" {}))]
    result))

(defrecord Node []
  protocol/Fs
  (mkdir! [_this dir]
    (ipc/ipc "mkdir" dir))
  (mkdir-recur! [_this dir]
    (ipc/ipc "mkdir-recur" dir))
  (readdir [_this dir]                   ; recursive
    (ipc/ipc "readdir" dir))
  (unlink! [_this repo path _opts]
    (ipc/ipc "unlink"
             (config/get-repo-dir repo)
             path))
  (rmdir! [_this _dir]
    ;; Too dangerious!!! We'll never implement this.
    nil)
  (read-file [_this dir path options]
    (let [path (concat-path dir path)]
      (ipc/ipc "readFile" path options)))
  (write-file! [this repo dir path content opts]
    (let [path (concat-path dir path)]
      (p/let [stat (p/catch
                       (protocol/stat this dir path)
                       (fn [_e] :not-found))
              sub-dir (first (util/get-dir-and-basename path))
              _ (protocol/mkdir-recur! this sub-dir)]
        (write-file-impl! this repo dir path content opts stat))))
  (rename! [_this _repo old-path new-path]
    (ipc/ipc "rename" old-path new-path))
  (stat [_this dir path]
    (let [path (concat-path dir path)]
      (ipc/ipc "stat" path)))
  (open-dir [_this _ok-handler]
    (open-dir))
  (get-files [_this path-or-handle _ok-handler]
    (ipc/ipc "getFiles" path-or-handle))
  (watch-dir! [_this dir]
    (ipc/ipc "addDirWatcher" dir))
  (unwatch-dir! [_this dir]
    (ipc/ipc "unwatchDir" dir)))
