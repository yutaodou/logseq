(ns frontend.extensions.fsrs
  (:require))

(def ratings
  {:again 1
   :hard 2
   :good 3
   :easy 4})

(def w [1, 1, 5, -0.5, -0.5, 0.2, 1.4, -0.12, 0.8, 2, -0.2, 0.2, 1])

(def enable-fuzz false)
(def requestRetention 0.9)
(def maximumInterval 36500)
;; const intervalModifier = Math.log(requestRetention) / Math.log(0.9);
(def interval-modifier (/ (js/Math.log requestRetention) (js/Math.log 0.9)))

(defn- constrain-difficulty
  [difficulty]
  {:pre [(number? difficulty)]}
  (js/Math.min (js/Math.max (.toFixed difficulty 2) 1) 10))


(defn- init-difficulty
  [rating]
  (parse-double (.toFixed (constrain-difficulty (+ (nth w 2) (* (nth w 3) (- (get ratings rating) 3)))) 2)))

(defn- init-stability
  [rating]
  (parse-double (.toFixed (js/Math.max (+ (nth w 0) (* (nth w 1) (- (get ratings rating) 1))) 0.1) 2)))

(defn- apply-fuzz
  [ivl]
  ivl)

(defn- next-interval
  [stability]
  (let [new-interval (apply-fuzz (* stability interval-modifier))]
    (js/Math.min maximumInterval (js/Math.max 1 (js/Math.round new-interval)))))

(defn- mean-reversion
  [init current]
  (+ (* (nth w 5) init) (* (- 1 (nth w 5)) current)))

(defn- next-difficulty
  [d rating]
  (let [next-d (+ d (* (nth w 4) (- (get ratings rating) 3)))]
    (constrain-difficulty (mean-reversion (nth w 2) next-d))))

(defn- next-recall-stability
  [d s r]
  (-> (* s (+ 1 (* (js/Math.exp (nth w 6)) (- 11 d) (js/Math.pow s (nth w 7)) (- (js/Math.exp (* (- 1 r) (nth w 8))) 1))))
      (.toFixed 2)
      parse-double))

(defn- next-forget-stability
  [d s r]
  (-> (* (nth w 9) (js/Math.pow d (nth w 10)) (js/Math.pow s (nth w 11)) (js/Math.exp (* (- 1 r) (nth w 12))))
      (.toFixed 2)
      parse-double))

(defn- init-states
  []
  {:again {:d (init-difficulty :again)
           :s (init-stability :again)}
   :hard {:d (init-difficulty :hard)
          :s (init-stability :hard)}
   :good {:d (init-difficulty :good)
          :s (init-stability :good)}
   :easy {:d (init-difficulty :easy)
          :s (init-stability :easy)}})
