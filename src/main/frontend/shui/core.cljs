(ns frontend.shui.core
  (:require 
    [frontend.shui.table :as shui.table]
    [frontend.shui.inline-block :as shui.inline-block]))

(def table shui.table/table)
(def inline->inline-block shui.inline-block/inline->inline-block)
(def inline->map-inline-block shui.inline-block/inline->map-inline-block)
