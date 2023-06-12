(ns lob-asset-management.logic.transaction)

(defn sell? [{:keys [type movement-type]}]
  (and (= type"Debito")
       (or (= movement-type "Transferência - Liquidação")
           (= movement-type "COMPRA / VENDA"))))

(defn buy? [{:keys [type movement-type]}]
  (and (= type "Credito")
       (or (= movement-type "Transferência - Liquidação")
           (= movement-type "COMPRA / VENDA"))))
