(ns electron.assets
  "Assets related handlers"
  (:require ["md5-file" :as md5File]
            ["/electron/utils" :as js-utils]
            ["path" :as node-path]
            ["fs-extra" :as fs]
            [promesa.core :as p]
            [cljs-bean.core :as bean]
            [medley.core :as medley]
            [clojure.string :as string]))

(defn get-md5-by-file-path
  [s]
  (when (string? s)
    (.sync md5File s)))

(def mem-md5-by-file-path
  (memoize get-md5-by-file-path))

(defn touch-graph-existent-asset!
  [graph-path src-path]
  (when-let [assets-path (and (string? graph-path) (node-path/join graph-path "assets"))]
    (let [src-ext (node-path/extname src-path)]
      (when (and (string? src-path)
                 (not (string/blank? src-ext))
                 (fs/existsSync src-path)
                 (fs/existsSync assets-path))
        (let [src-md5 (get-md5-by-file-path src-path)]
          (-> (js-utils/getAllFiles assets-path (clj->js [src-ext]))
              (p/then (fn [targets]
                        (some->> (bean/->clj targets)
                                 (medley/find-first #(= src-md5 (mem-md5-by-file-path (:path %)))) :path)))
              (p/catch #(js/console.debug "[Existent asset] " %))))))))