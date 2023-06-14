(ns lob-asset-management.logic.portfolio)

(defn position-profit-loss-value
  [position-value total-cost]
  (if (> position-value 0M)
    (- position-value total-cost)
    0.0))

(defn position-profit-loss-percentage
  [total-cost profit-loss]
  (if (and (> total-cost 0M)
           (not (= profit-loss 0M)))
    (bigdec (* (/ profit-loss total-cost) 100))
    0.0))

(defn position-percentage
  [total position-value]
  (if (and (> total 0M) (> position-value 0M))
    (bigdec (* 100 (/ position-value total)))
    0.0))
