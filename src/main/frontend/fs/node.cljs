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
            [frontend.fs.write :as write]))

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


(defn- write-file-impl!
  [this repo dir path content {:keys [ok-handler error-handler old-content skip-compare?] :as opts} stat]
  (write/write-file! this repo dir path content
                     (assoc opts
                            :write-file-fn (fn [repo path content]
                                             (ipc/ipc "writeFile" repo path content))
                            :backup-file-fn (fn [repo path disk-content content]
                                              (ipc/ipc "backupDbFile" (config/get-local-dir repo) path disk-content content)))
                     stat))

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
