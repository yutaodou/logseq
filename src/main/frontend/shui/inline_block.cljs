(ns frontend.shui.inline-block
  (:require
    [cljs.core.match :refer [match]]))

(declare inline)

(defn map-inline
  [config col]
  (map #(inline config %) col))

(defn inline->inline-block [inline]
  (fn [context {:keys [html-export?] :as config} item]
    (match (cons context item)
      :else (inline config item))))

(defn inline->map-inline-block [inline]
  (fn [config col]
    (map #(inline config %) col)))
