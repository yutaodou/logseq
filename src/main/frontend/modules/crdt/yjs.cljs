(ns frontend.modules.crdt.yjs
  (:require ["/frontend/utils" :as utils]
            ["yjs" :as y]
            ["buffer" :as buffer]
            [goog.object :as gobj]
            [frontend.idb :as idb]
            [frontend.state :as state]
            [promesa.core :as p]
            [clojure.string :as string]
            [cljs-bean.core :as bean]))

(defonce YDoc (gobj/get y "Doc"))

(def serialize (gobj/get y "encodeStateAsUpdate"))
(def apply-update (gobj/get y "applyUpdate"))

(defn new-doc
  []
  (new YDoc))

(defn y-text
  [^js doc]
  (.getText doc))

(defn init-y-text!
  [doc content]
  (when (string? content)
    (.insert ^js (y-text doc) 0 content)
    doc))

(defn get-doc-text
  [doc]
  (when doc (.toString (y-text doc))))

(defn get-ytext-deltas
  [s1 s2]
  (utils/getDeltaOperations s1 s2))

(defn apply-delta!
  [doc deltas]
  (.applyDelta ^js (y-text doc) deltas)
  doc)

(defn set-file-db-doc!
  [path doc]
  (when (and path doc)
    (let [result (serialize doc)]
      (idb/set-item! (str "ydoc-" path) result))))

(defn template-doc->base64
  [content]
  (when content
    (let [doc (new-doc)]
      (init-y-text! doc content)
      (let [b (new (gobj/get buffer "Buffer") (serialize doc))]
        (.toString b "base64")))))

(defn get-file-db-doc
  [path]
  (when path
    (p/let [cached-doc (get-in @state/state [:file-db/crdt path])
            idb-cache (idb/get-item (str "ydoc-" path))
            doc (or cached-doc
                    (let [doc (new-doc)]
                      (when idb-cache
                        (apply-update doc idb-cache))
                      doc))]
      (.on doc "update" (fn [_update _origin]
                          (prn "set new doc: " {:path path
                                                :content (get-doc-text doc)})
                          (set-file-db-doc! path doc)))
      (state/set-state! [:file-db/crdt path] doc)
      doc)))

(defn delete-file-db-doc!
  [path]
  (swap! state/state dissoc :file-db/crdt path))

(defn merge-docs!
  [path new-ydoc deltas]
  (p/let [doc (get-file-db-doc path)
          doc (if (seq deltas) (apply-delta! doc deltas) doc)]
    (if new-ydoc (apply-update doc (serialize new-ydoc)) doc)))

(defn merge-template-doc!
  [template-doc]
  (let [doc (new-doc)]
    ;; To avoid duplications when merging conflicts from multiple clients
    ;; TODO: fork yjs so that random clientIDs can't be the value `(js/Math.pow 2 52)`
    (set! (.-clientID template-doc) (js/Math.pow 2 52))
    (apply-update doc (serialize template-doc))
    doc))

(comment
  ;; reorder
  (def d1 (new-doc))
  (def initial-text "- foo\n- bar")
  (init-y-text! d1 initial-text)

  (def updates (serialize d1))
  (def d2 (new-doc))
  (apply-update d2 updates)

  (.insert (y-text d1) 11 "\n- baz")
  (get-doc-text d1)

  ;; adjust blocks order
  (def new-text "- bar\n- foo")
  (def deltas (get-ytext-deltas initial-text new-text))
  (apply-delta! d2 deltas)

  (def updates (serialize d1))
  (apply-update d2 updates)
  (prn "Merged d2: " (get-doc-text d2))

  (def updates (serialize d2))
  (apply-update d1 updates)
  (prn "Merged d1: "(get-doc-text d1))

  ;; append chars
  (def d1 (new-doc))
  (init-y-text! d1 initial-text)

  (def updates (serialize d1))
  (def d2 (new-doc))
  (apply-update d2 updates)

  (def new-text "- foo\n- bar1")
  (def deltas (get-ytext-deltas initial-text new-text))
  (apply-delta! d1 deltas)

  (get-doc-text d1)

  ;; adjust blocks order
  (def new-text (str initial-text "2"))
  (def deltas (get-ytext-deltas initial-text new-text))
  (apply-delta! d2 deltas)

  (def updates (serialize d1))
  (apply-update d2 updates)
  (prn "Merged d2: " (get-doc-text d2))

  (def updates (serialize d2))
  (apply-update d1 updates)
  (prn "Merged d1: "(get-doc-text d1))

  ;; insert && delete
  (def d1 (new-doc))
  (init-y-text! d1 initial-text)

  (def updates (serialize d1))
  (def d2 (new-doc))
  (apply-update d2 updates)

  (def new-text "- bar")
  (def deltas (get-ytext-deltas initial-text new-text))
  (apply-delta! d1 deltas)

  (get-doc-text d1)

  (def new-text (str initial-text "\n test"))
  (def deltas (get-ytext-deltas initial-text new-text))
  (apply-delta! d2 deltas)

  (def updates (serialize d1))
  (apply-update d2 updates)
  (prn "Merged d2: " (get-doc-text d2))

  (def updates (serialize d2))
  (apply-update d1 updates)
  (prn "Merged d1: "(get-doc-text d1))

  (defn dup-merge-template-doc!
    [template-doc]
    (let [doc (new-doc)]
      (apply-update doc (serialize template-doc))
      doc))

  (def template-doc (-> (new-doc)
                        (init-y-text! "- foo\n- bar")))

  (def d1 (dup-merge-template-doc! template-doc))
  (def d2 (dup-merge-template-doc! (set! (.-clientID template-doc) (rand-int 10000))))

  (apply-update d1 (serialize d2))

  (prn "Merge (duplicated): " (get-doc-text d1))
  )
