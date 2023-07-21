(ns lob-asset-management.logic.transaction)

(defn sell? [{:keys [type movement-type]}]
  (and (= type"Debito")
       (or (= movement-type "Transferência - Liquidação")
           (= movement-type "COMPRA / VENDA"))))

(defn buy? [{:keys [type movement-type]}]
  (and (= type "Credito")
       (or (= movement-type "Transferência - Liquidação")
           (= movement-type "COMPRA / VENDA"))))

(defn already-exist?
  [id db-data]
  (if (empty? db-data)
    false
    (let [db-data-id (->> db-data (map :transaction/id) set)]
      (contains? db-data-id id))))
