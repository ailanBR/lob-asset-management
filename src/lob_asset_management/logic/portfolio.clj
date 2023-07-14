(ns lob-asset-management.logic.portfolio
  (:require [lob-asset-management.aux.money :refer [safe-dob]]))

(defn position-profit-loss-value
  [position-value total-cost]
  (if (> position-value 0M)
    (- position-value total-cost)
    0.0))

(defn position-profit-loss-percentage
  [total-cost profit-loss]
  (if (and (> total-cost 0M) (not= profit-loss 0M))
    (bigdec (* (/ profit-loss total-cost) 100))
    0.0))

(defn position-percentage
  [total position-value]
  (if (and (> total 0M) (> position-value 0M))
    (bigdec (* 100 (/ position-value total)))
    0.0))

(defn filter-operation
  [allowed-operations transaction]
  (filter #(contains?
             allowed-operations
             (:transaction/operation-type %))
          transaction))

(defn remove-fixed-income
  [transaction]
  (remove #(clojure.string/includes?
             (-> % :transaction.asset/ticket name) "CDB")
          transaction))


(defn total-operation
  [quantity average-price operation-total]
  (let [calculated-total (* quantity average-price)]
    (if (> 0M calculated-total)
      calculated-total
      operation-total)))

(defn sell-total-cost
  [average-price quantity portfolio-quantity portfolio-average-price]
  (let [tt-t (* average-price quantity)
        tt-c (* (safe-dob portfolio-quantity) (safe-dob portfolio-average-price))]
    (- tt-t tt-c)))


(defn transaction-total-and-portfolio-cost
  [portfolio-quantity
   portfolio-average-price
   transaction-quantity
   transaction-average-price]
  (let [tt-t (* transaction-average-price transaction-quantity)
        tt-c (* (safe-dob portfolio-quantity) (safe-dob portfolio-average-price))]
    (+ tt-t tt-c)))

(def sell-total-cost (comp - transaction-total-and-portfolio-cost))
(def buy-total-cost (comp + transaction-total-and-portfolio-cost))
