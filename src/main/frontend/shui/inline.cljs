(ns frontend.shui.inline
  (:require
    [cljs.core.match :refer [match]]))

(declare inline)

(defn ->elem
  ([elem items]
   (->elem elem nil items))
  ([elem attrs items]
   (let [elem (keyword elem)]
     (if attrs
       (vec
        (cons elem
              (cons attrs
                    (seq items))))
       (vec
        (cons elem
              (seq items)))))))

(defn map-inline
  [config col]
  (map #(inline config %) col))

(defn- inline-emphasis
  [config kind data]
  (let [elem (case kind
               "Bold" :b
               "Italic" :i
               "Underline" :ins
               "Strike_through" :del
               "Highlight" :mark)]
    (->elem elem (map-inline config data))))

(defn inline
  [{:keys [html-export?] :as config} item]
  (match item
         [(:or "Plain" "Spaces") s]
         s

         ["Superscript" l]
         (->elem :sup (map-inline config l))
         ["Subscript" l]
         (->elem :sub (map-inline config l))

         ; ["Tag" _]
         ; (when-let [s (gp-block/get-tag item)]
         ;   (let [s (text/page-ref-un-brackets! s)]
         ;     (page-cp (assoc config :tag? true) {:block/name s})))

         ["Emphasis" [[kind] data]]
         (inline-emphasis config kind data)

         ; ["Entity" e]
         ; [:span {:dangerouslySetInnerHTML
         ;         {:__html (security/sanitize-html (:html e))}}]

         ; ["Latex_Fragment" [display s]] ;display can be "Displayed" or "Inline"
         ; (if html-export?
         ;   (latex/html-export s false true)
         ;   (latex/latex (str (d/squuid)) s false (not= display "Inline")))

         [(:or "Target" "Radio_Target") s]
         [:a {:id s} s]

         ["Email" address]
         (let [{:keys [local_part domain]} address
               address (str local_part "@" domain)]
           [:a {:href (str "mailto:" address)} address])

         ; ["Nested_link" link]
         ; (nested-link config html-export? link)

         ; ["Link" link]
         ; (link-cp config html-export? link)

         ; [(:or "Verbatim" "Code") s]
         ; [:code s]

         ; ["Inline_Source_Block" x]
         ; [:code (:code x)]

         ; ["Export_Snippet" "html" s]
         ; (when (not html-export?)
         ;   [:span {:dangerouslySetInnerHTML
         ;           {:__html (security/sanitize-html s)}}])

         ; ["Inline_Hiccup" s] ;; String to hiccup
         ; (ui/catch-error
         ;  [:div.warning {:title "Invalid hiccup"} s]
         ;  [:span {:dangerouslySetInnerHTML
         ;          {:__html (hiccup->html s)}}])

         ; ["Inline_Html" s]
         ; (when (not html-export?)
         ;   ;; TODO: how to remove span and only export the content of `s`?
         ;   [:span {:dangerouslySetInnerHTML {:__html (security/sanitize-html s)}}])

         ; [(:or "Break_Line" "Hard_Break_Line")]
         ; [:br]

         ; ["Timestamp" [(:or "Scheduled" "Deadline") _timestamp]]
         ; nil
         ; ["Timestamp" ["Date" t]]
         ; (timestamp t "Date")
         ; ["Timestamp" ["Closed" t]]
         ; (timestamp t "Closed")
         ; ["Timestamp" ["Range" t]]
         ; (range t false)
         ; ["Timestamp" ["Clock" ["Stopped" t]]]
         ; (range t true)
         ; ["Timestamp" ["Clock" ["Started" t]]]
         ; (timestamp t "Started")

         ; ["Cookie" ["Percent" n]]
         ; [:span {:class "cookie-percent"}
         ;  (util/format "[d%%]" n)]
         ; ["Cookie" ["Absolute" current total]]
         ; [:span {:class "cookie-absolute"}
         ;  (util/format "[%d/%d]" current total)]

         ; ["Footnote_Reference" options]
         ; (let [{:keys [name]} options
         ;       encode-name (util/url-encode name)]
         ;   [:sup.fn
         ;    [:a {:id (str "fnr." encode-name)
         ;         :class "footref"
         ;         :on-click #(route-handler/jump-to-anchor! (str "fn." encode-name))}
         ;     name]])

         ; ["Macro" options]
         ; (macro-cp config options)

         :else ""))

