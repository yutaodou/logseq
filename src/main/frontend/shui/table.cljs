(ns frontend.shui.table
  (:require 
    [clojure.string :as str]
    [frontend.shui.util :refer [use-ref-bounding-client-rect use-dom-bounding-client-rect $main-content] :as util]
    [frontend.date :refer [int->local-time-2]]
    [rum.core :as rum]))

; [[["Plain" "A"]] 
;  [["Emphasis" [["Italic"] [["Plain" "B"]]]]]]

(def BORDER_WIDTH 0.25)
(def MAX_WIDTH 50)
(def COLORS #{"tomato" "red" "crimson" "pink" "plum" "purple" "violet" "indigo" "blue" "sky" "cyan" "teal" "mint" "green" "grass" "lime" "yellow" "amber" "orange" "brown"})

(defn color->gray [color]
  (case color 
    ("tomato" "red" "crimson" "pink" "plum" "purple" "violet") "mauve"
    ("indigo" "blue" "sky" "cyan") "slate" 
    ("teal" "mint" "green") "sage"
    ("grass" "lime") "olive"
    ("yellow" "amber" "orange" "brown") "sand"
    nil))

(defn color->header-gradient
  "These are state explicitly so tailwind can pick them up"
  [color]
  (case color 
    "tomato"  "from-tomato-4  to-tomato-5" 
    "red"     "from-red-4     to-red-5"
    "crimson" "from-crimson-4 to-crimson-5"
    "pink"    "from-pink-4    to-pink-5"
    "plum"    "from-plum-4    to-plum-5"
    "purple"  "from-purple-4  to-purple-5"
    "violet"  "from-violet-4  to-violet-5"
    "indigo"  "from-indigo-4  to-indigo-5"
    "blue"    "from-blue-4    to-blue-5"
    "sky"     "from-sky-4     to-sky-5"
    "cyan"    "from-cyan-4    to-cyan-5"
    "teal"    "from-teal-4    to-teal-5"
    "mint"    "from-mint-4    to-mint-5"
    "green"   "from-green-4   to-green-5"
    "grass"   "from-grass-4   to-grass-5"
    "lime"    "from-lime-4    to-lime-5"
    "yellow"  "from-yellow-4  to-yellow-5"
    "amber"   "from-amber-4   to-amber-5"
    "orange"  "from-orange-4  to-orange-5"
    "brown"   "from-brown-4   to-brown-5"))

(defn color->bar 
  "These are state explicitly so tailwind can pick them up"
  [color]
  (case color 
    ; "tomato"  "from-tomato-6  via-tomato-9  to-tomato-8" 
    ; "red"     "from-red-6     via-red-9     to-red-8"
    ; "crimson" "from-crimson-6 via-crimson-9 to-crimson-8"
    ; "pink"    "from-pink-6    via-pink-9    to-pink-8"
    ; "plum"    "from-plum-6    via-plum-9    to-plum-8"
    ; "purple"  "from-purple-6  via-purple-9  to-purple-8"
    ; "violet"  "from-violet-6  via-violet-9  to-violet-8"
    ; "indigo"  "from-indigo-6  via-indigo-9  to-indigo-8"
    ; "blue"    "from-blue-6    via-blue-9    to-blue-8"
    ; "sky"     "from-sky-6     via-sky-9     to-sky-8"
    ; "cyan"    "from-cyan-6    via-cyan-9    to-cyan-8"
    ; "teal"    "from-teal-6    via-teal-9    to-teal-8"
    ; "mint"    "from-mint-6    via-mint-9    to-mint-8"
    ; "green"   "from-green-6   via-green-9   to-green-8"
    ; "grass"   "from-grass-6   via-grass-9   to-grass-8"
    ; "lime"    "from-lime-6    via-lime-9    to-lime-8"
    ; "yellow"  "from-yellow-6  via-yellow-9  to-yellow-8"
    ; "amber"   "from-amber-6   via-amber-9   to-amber-8"
    ; "orange"  "from-orange-6  via-orange-9  to-orange-8"
    ; "brown"   "from-brown-6   via-brown-9   to-brown-8"
    "tomato"  "from-tomatodark-6  via-tomatodark-9  to-tomatodark-8" 
    "red"     "from-reddark-6     via-reddark-9     to-reddark-8"
    "crimson" "from-crimsondark-6 via-crimsondark-9 to-crimsondark-8"
    "pink"    "from-pinkdark-6    via-pinkdark-9    to-pinkdark-8"
    "plum"    "from-plumdark-6    via-plumdark-9    to-plumdark-8"
    "purple"  "from-purpledark-6  via-purpledark-9  to-purpledark-8"
    "violet"  "from-violetdark-6  via-violetdark-9  to-violetdark-8"
    "indigo"  "from-indigodark-6  via-indigodark-9  to-indigodark-8"
    "blue"    "from-bluedark-6    via-bluedark-9    to-bluedark-8"
    "sky"     "from-skydark-6     via-skydark-9     to-skydark-8"
    "cyan"    "from-cyandark-6    via-cyandark-9    to-cyandark-8"
    "teal"    "from-tealdark-6    via-tealdark-9    to-tealdark-8"
    "mint"    "from-mintdark-6    via-mintdark-9    to-mintdark-8"
    "green"   "from-greendark-6   via-greendark-9   to-greendark-8"
    "grass"   "from-grassdark-6   via-grassdark-9   to-grassdark-8"
    "lime"    "from-limedark-6    via-limedark-9    to-limedark-8"
    "yellow"  "from-yellowdark-6  via-yellowdark-9  to-yellowdark-8"
    "amber"   "from-amberdark-6   via-amberdark-9   to-amberdark-8"
    "orange"  "from-orangedark-6  via-orangedark-9  to-orangedark-8"
    "brown"   "from-browndark-6   via-browndark-9   to-browndark-8"))

(defn rdx
  ([color step] (str "bg-" color "-" step))
  ([param color step] (str param "-" color "-" step)))
  ; ([color step] (str "bg-" color "dark-" step))
  ; ([param color step] (str param "-" color "dark-" step)))

(defn last-str 
  "Given an inline AST, return the last string element you can walk to" 
  [inline]
  (cond 
    (keyword? inline) (name inline)
    (string? inline) inline
    (coll? inline) (last-str (last inline))
    :else (pr-str inline)))

(comment
  (last-str "A")
  (last-str ["Plain" "A"])
  (last-str [["Plain" "A"]])
  (last-str [["Plain" "A"] 
             [["Emphasis" [["Italic"] [["Plain" "B"]]]]]]))

(defn print-or-map-inline [{:keys [map-inline]} inline? data]
  (cond 
    (sequential? inline?) (map-inline data inline?)
    (string? inline?) inline?
    (keyword? inline?) (name inline?)
    (boolean? inline?) (pr-str inline?) 
    (number? inline?) (if-let [date (int->local-time-2 inline?)]
                        date inline?)))
    

(rum/defc table-header
  [data {:keys [inline order template cell-x cell-y cursor-x cursor-y max-cell-x hover gray color hover-color? borders?] :as _config}]
  (let [highlight? (case hover 
                     ("col" "all") (= cell-x cursor-x) 
                     "cell" (= [cell-x cell-y] [cursor-x cursor-y])
                     false)
        highlight-color (if hover-color? color gray)]
    [:div.py-2.px-2.text-xs.font-semibold.tracking-wider.uppercase.bg-gradient-to-b
     {:style {:order order
              :min-width (str (min 20 (:min template)) "rem")
              :max-width (str MAX_WIDTH "rem")
              :box-sizing :border-box
              :box-shadow (when borders? "0 0 0 1px var(--tw-shadow-color)")}
      :class (str (if highlight? (rdx highlight-color 5) (rdx color 4))
                  " " 
                  ; (color->header-gradient color)
                  " " 
                  (rdx "text" color 11)
                  " "
                  (cond (= cell-x 0) "rounded-tl"
                        (= (inc cell-x) max-cell-x) "rounded-tr"
                        :else "")
                  " " 
                  (rdx "shadow" color 7))}
     (print-or-map-inline _config data inline)]))

(rum/defc table-cell 
  [data {:keys [inline order template cell-x cell-y max-cell-x max-cell-y cursor-x cursor-y striped? set-cursor hover color gray hover-color?] :as _config}]
  (let [first-col? (= 0 cell-x) 
        last-col? (= cell-x max-cell-x)
        last-row? (= cell-y max-cell-y)
        highlight? (case hover 
                      "cell" (= [cell-x cell-y] [cursor-x cursor-y])
                      "row" (= cursor-y cell-y) 
                      "col" (= cursor-x cell-x) 
                      "all" (or (= cursor-x cell-x) (= cursor-y cell-y))
                      false)
        bg-color (cond 
                   (and highlight? (= [cell-x cell-y] [cursor-x cursor-y])) (if hover-color? (rdx color 3) (rdx gray 4))
                   highlight? (if hover-color? (rdx color 2) (rdx gray 3))
                   (and striped? (even? cell-y)) (rdx gray 2)
                   :else (rdx gray 1))]
    [:div.py-2.px-2
     {:style {:order order
              :max-width (str MAX_WIDTH "rem")}
              ; :max-width (str (:min template) "rem")}} 
      :class (cond-> bg-color
               (and last-row? first-col?) (str " rounded-bl") 
               (and last-row? last-col?) (str " rounded-br"))
      :on-pointer-enter #(set-cursor [cell-x cell-y])}
     (print-or-map-inline _config data inline)]))

;; We have a list of columns that the data relates to
;; We than have a list of rows with lists of data pertaining to the columns 
;; We then have an ordered list of columns to show 
;; For each row, we want to show columns in that order

; [[[1 2 3] 
;   [4 5 6] 
;   [7 8 9]]
;  [[1 2 3] 
;   [4 5 6] 
;   [7 8 9]]]

(rum/defc table
  [data {:keys [cols] :as config}]
  ; (println "shui cols" (pr-str cols))
  ; (println "shui data" (pr-str data))
  (let [[[cursor-x cursor-y] set-cursor] (rum/use-state [])
        [root-ref root-rect] (use-ref-bounding-client-rect)
        main-content-rect (use-dom-bounding-client-rect ($main-content))
        display-cols (map str/lower-case (or (some-> (get-in config [:block :block/properties :table/cols]) 
                                                     (str/split #", ?"))
                                             (map last-str cols)))
        template-cols (volatile! (mapv (fn [inline] {:width nil :min (count (last-str inline)) :max nil}) display-cols))
        borders? (get-in config [:block :block/properties :table/borders])
        striped? (get-in config [:block :block/properties :table/striped])
        hover (get-in config [:block :block/properties :table/hover] "none")
        color (get COLORS (get-in config [:block :block/properties :table/color]) "grass")
        hover-color? (get-in config [:block :block/properties :table/hover-color])
        gray (color->gray color)
        _ (doall
            (let [cell-index (volatile! (count display-cols))]
              (for [[group-index group-data] (map-indexed vector data) 
                    [row-index row-data] (map-indexed vector group-data)
                    [col-index col-data] (map-indexed vector row-data) 
                    :let [default-col (nth cols col-index)
                          default-col-name (last-str default-col)
                          display-col-index (.indexOf display-cols (str/lower-case default-col-name)) 
                          cell-index' @cell-index
                          cell-y (quot cell-index' (count display-cols))
                          cell-x display-col-index] 
                    :when (>= display-col-index 0)
                    :let [order (+ (* cell-y (count display-cols)) cell-x)
                          _ (js/console.log "shui order" (last-str col-data) order (str (count display-cols) " + " (inc group-index) " * " (inc row-index) " + " col-index)) 
                          _ (vswap! template-cols update-in [display-col-index :min] max (count (str/lower-case (last-str col-data))))]] 
                (vswap! cell-index inc))))
        template-cols @template-cols
        max-cell-x (count display-cols)
        max-cell-y (reduce + 0 (map count data)) 
        headers (for [[col-index inline-data] (map-indexed vector cols) 
                      :let [order (.indexOf display-cols (str/lower-case (last-str inline-data)))]
                      :when (>= order 0)
                      :let [template (nth template-cols order)]]
                   (table-header inline-data (assoc config 
                                                    :cell-x order
                                                    :cell-y 0
                                                    :max-cell-x max-cell-x
                                                    :cursor-x cursor-x 
                                                    :cursor-y cursor-y
                                                    :order order
                                                    :template template
                                                    :hover hover
                                                    :color color 
                                                    :gray gray
                                                    :hover-color? hover-color?
                                                    :striped? striped?
                                                    :borders? borders?)))
        cells   (for [[group-index group-data] (map-indexed vector data)
                      [row-index row-data] (map-indexed vector group-data)
                      [col-index inline-data] (map-indexed vector row-data) 
                      :let [default-col (nth cols col-index)
                            default-col-name (str/lower-case (last-str default-col))
                            cell-x (.indexOf display-cols default-col-name) 
                            cell-y (* (inc row-index) (inc group-index))]
                      :when (>= cell-x 0)
                      :let [order (+ (* (count display-cols) cell-y) cell-x)
                            template (nth template-cols cell-x)]]
                  (table-cell inline-data (assoc config 
                                                 :cell-x cell-x 
                                                 :cell-y cell-y
                                                 :cursor-x cursor-x 
                                                 :cursor-y cursor-y
                                                 :max-cell-x max-cell-x 
                                                 :max-cell-y max-cell-y
                                                 :set-cursor set-cursor
                                                 :col default-col
                                                 :col-index col-index 
                                                 :group-index group-index
                                                 :order order
                                                 :row-index row-index
                                                 :template template
                                                 :striped? striped?
                                                 :hover hover 
                                                 :color color 
                                                 :gray gray
                                                 :hover-color? hover-color?)))
        total-col-min-widths-px (->> (map :min template-cols) 
                                     (map #(min 30 %)) 
                                     (reduce +) 
                                     util/rem->px)
        _ (js/console.log "shui total-col-min-widths" 
                          (pr-str total-col-min-widths-px) 
                          (pr-str (map :min template-cols)))
                          ; "root-rect" (clj->js root-rect)
                          ; "main-rect" (clj->js main-content-rect))
        ;; TODO: Have a fractional unit one if we want smaller tables to expand?
        grid-template-columns (str "repeat(" (count display-cols) ", auto)")

        left-adjustment (- (:left root-rect) (:left main-content-rect))
        right-adjustment (- (:width main-content-rect) 
                            (- (:right root-rect) (:left main-content-rect)))]
        
    [:div.border-2.border-red-500 {:ref root-ref}
     [:div.opacity-50 {:style {:width (:width main-content-rect)
                               :margin-left (- (:left main-content-rect) (:left root-rect))
                               :padding-left left-adjustment
                               :padding-right right-adjustment
                               :overflow-x "scroll"}}
                               ; :transform (str "translateX(calc(-50% + 300px))")}
      ; [:div (pr-str root-rect)]
      [:div.grid.rounded.border {:style {:grid-template-columns grid-template-columns
                                         :gap (when borders? "1px")
                                         ; :width (some-> root-rect :width (< total-col-min-widths-px) (and total-col-min-widths-px))
                                         ; :width   total-col-min-widths-px
                                         :width (when (and (:width root-rect) 
                                                           (< (:width root-rect) total-col-min-widths-px))
                                                  total-col-min-widths-px)}
                                 ; :class (str (rdx color 5) " " (rdx "border" color 5))
                                 :class (str (rdx gray 7) " " (rdx "border" gray 7))
                                 :data-grid-template-columns grid-template-columns
                                 :on-pointer-leave #(set-cursor [])}
       ; [:div.bg-gradient-to-r.rounded-t.h-2.-ml-px.-mt-px.-mr-px {:style {:grid-column "1 / -1"} 
       ;                                                            :class (color->bar color)}]
       (concat headers cells)]]]))
