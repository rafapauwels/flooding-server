(in-ns 'servidor.core)

(def alvos [{:endereco-ip "localhost" :porta 9443}]) ; Usar peers da cloud (slurp http://cloud)

(defn escolhe-servidor-alvo
  "Escolhe um alvo da lista de alvos aleatoriamente"
  [alvos]
  (rand-nth alvos))
