(ns frontend.components.block-effects
  (:require [clojure.string :as string]
            [rum.core :as rum]
            [dommy.core :as d]
            [frontend.rum :as frontend-rum]))

;; {block-uuid timestamp}
(defonce *effects (atom {}))

(defn clear-effects!
  [] (reset! *effects {}))

(defn dorun-effects!
  []
  (doseq [[k] @*effects]
    (swap! *effects assoc k (js/Date.now)))
  @*effects)

(defn collect-block-sibling-effects!
  [block-uuid]
  (let [^js target (js/document.querySelector (str "#ls-block-" (str block-uuid)))]
    (when (some-> target (d/has-class? "is-order-list"))
      (clear-effects!)
      (loop [^js node (.-nextElementSibling target)]
        (when node
          (let [uuid (d/attr node "blockid")]
            (when (and (not (string/blank? uuid))
                    ;; order list block
                    (d/has-class? node "is-order-list"))
              (swap! *effects assoc uuid nil))
            (recur (.-nextElementSibling node))))))
    @*effects))

(rum/defc block-effects-observer < rum/static
  [block-cp block-uuid editing?]
  (let [v (some-> (frontend-rum/use-atom-in *effects [block-uuid]) (first))
        mounted?-fn (frontend-rum/use-mounted)]
    (rum/use-effect!
      (fn []
        (when (and (mounted?-fn) v)
          (rum/request-render block-cp)
          ))
      [v])

    (rum/use-effect!
      (fn [] #(swap! *effects dissoc block-uuid)) [])

    (rum/use-effect!
      (fn []
        (when (mounted?-fn)
          (let [timer (when editing?
                        (let [value (some-> (js/document.querySelector (str "#edit-block-" block-uuid)) (.-value))]
                          (when (string/blank? value)
                            ;; for selected block
                            (js/setTimeout
                              (fn []
                                (collect-block-sibling-effects! block-uuid)
                                (dorun-effects!)) 0))))]
            #(when timer (js/clearTimeout timer)))))
      [editing?])
    nil))
