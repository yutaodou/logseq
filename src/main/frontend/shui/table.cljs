(ns frontend.shui.table
  (:require 
    [clojure.string :as str]
    [frontend.shui.inline :refer [map-inline]]
    [rum.core :as rum]))

; [[["Plain" "A"]] 
;  [["Emphasis" [["Italic"] [["Plain" "B"]]]]]]

(defn last-str 
  "Given an inline AST, return the last string element you can walk to" 
  [inline]
  (cond 
    (string? inline) inline
    (coll? inline) (last-str (last inline))
    :else nil))

(comment
  (last-str "A")
  (last-str ["Plain" "A"])
  (last-str [["Plain" "A"]])
  (last-str [["Plain" "A"] 
             [["Emphasis" [["Italic"] [["Plain" "B"]]]]]]))

(rum/defcs table-header < rum/reactive 
  [state data {:keys [inline order] :as _config}]
  [:div {:style {:order order}} (map-inline inline data)])

(rum/defcs table-cell < rum/reactive 
  [state data {:keys [inline order] :as _config}]
  [:div {:style {:order order}} (map-inline inline data)])

;; We have a list of columns that the data relates to
;; We than have a list of rows with lists of data pertaining to the columns 
;; We then have an ordered list of columns to show 
;; For each row, we want to show columns in that order

(rum/defcs table < rum/reactive 
  [state data {:keys [cols] :as config}]
  (println "shui table state" (pr-str state))
  (println "shui table data" (pr-str data)) 
  (println "shui table config" (pr-str config))
  (let [display-cols (or (some-> (get-in config [:inline :block :block/properties :table/cols]) 
                                 (str/split #", ?"))
                         (map last-str cols))
        headers (for [[col-index inline-data] (map-indexed vector cols) 
                      :let [order (.indexOf display-cols (last-str inline-data))]
                      :when (>= order 0)]
                   (table-header inline-data (assoc config 
                                                    :order order)))
        cells   (for [[group-index group-data] (map-indexed vector data)
                      [row-index row-data] (map-indexed vector group-data)
                      [col-index inline-data] (map-indexed vector row-data) 
                      :let [default-col (nth cols col-index)
                            display-col-index (.indexOf display-cols (last-str default-col))] 
                      :when (>= display-col-index 0)
                      :let [order (+ (* (count display-cols) (inc row-index)) display-col-index)]]
                  (table-cell inline-data (assoc config 
                                                 :col default-col
                                                 :col-index col-index 
                                                 :display-col-index display-col-index 
                                                 :group group-index
                                                 :order order
                                                 :row-index row-index)))] 
    (println "shui table display cols" (pr-str display-cols))
    (println "shui table headers" (pr-str headers))
    (println "shui table cells" (count cells) (pr-str cells))
    [:div.grid {:style {:grid-template-columns (str "repeat(" (count display-cols) ", minmax(0, 1fr))")}}
     (concat headers cells)]))
