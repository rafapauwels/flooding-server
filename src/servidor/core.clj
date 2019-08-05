(ns servidor.core)
(import '[java.net DatagramSocket
          DatagramPacket
          InetSocketAddress])

(def lista-arquivos (atom '()))
(def alvos [{:endereco-ip "localhost" :porta 9443}]) ; Usar peers da cloud (slurp http://cloud)

(defn recebe-requisicao
  "Bloqueante até receber uma requesição UDP através do socket especificado. Passa o payload adiante"
  [^DatagramSocket socket]
  (let [buffer (byte-array 512)
        pacote (DatagramPacket. buffer 512)]
    (.receive socket pacote)
    (String. (.getData pacote)
             0 (.getLength pacote))))

(defn loop-recebimento
  "Recebe uma requisição pelo socket esquecificado e aplica a função f sobre a mensagem"
  [socket f]
  (future (while true (f (recebe-requisicao socket)))))

(defn atualiza-arquivos!
  "Atualiza a lista atômica lista-arquivos com todos os arquivos (recursivamente) do diretório."
  [diretorio]
  (let [dire (clojure.java.io/file diretorio)]
    (swap! lista-arquivos 
           (fn [estado-atual] (map str (filter #(.isFile %) (file-seq dire)))))))

(defn loop-atualizacao-arquivos
  "Atualiza a cada 5000ms a lista de arquivos com os dados do diretório"
  [diretorio]
  (future (while true ((Thread/sleep 5000)
                       (atualiza-arquivos! diretorio)))))

(defn procura-arquivo
  "Monta um regex dinamicamente e aplica sobre a lista atômica de arquivos, retornando o primeiro arquivo encontrado dentro do diretório"
  [arquivo]
  (first 
   (filter
    #(boolean %) 
    (map 
     #(re-find 
       (re-pattern (str ".*/" arquivo "$")) %)
     @lista-arquivos))))

(defn enviar-query
  "Envia uma requisição através de um socket para o alvo, definido pelo endereço e porta. Caso a mensagem ultrapasse 512 bytes
  ela será truncada"
  [^DatagramSocket socket query alvo]
  (let [payload (.getBytes (str query))
        tamanho-requisicao (min (alength payload) 512)
        endereco (InetSocketAddress. (:endereco-ip alvo) (:porta alvo))
        pacote (DatagramPacket. payload tamanho-requisicao endereco)]
    (.send socket pacote)))

(defn escolhe-servidor-alvo
  "Escolhe um alvo da lista de alvos aleatoriamente"
  [alvos]
  (rand-nth alvos))

(defn diminui-ttl
  "Diminui TTL da requisição por 1"
  [requisicao]
  (assoc requisicao 
         :time-to-live (dec
                        (:time-to-live requisicao))))

(defn repassa-busca
  "Repassa a busca modificando seu TTL"
  [requisicao]
  (let [servidor-alvo (escolhe-servidor-alvo alvos)]
    (println (str "Repassando query " requisicao " para " servidor-alvo))
    (enviar-query socket
                  (str (diminui-ttl requisicao))
                  servidor-alvo)))

(defn trata-requisicao-de-busca
  "Recebe requisicao. Se o TTL > 0 inicia transferência tcp ou repassa requisição, do contrário retorna nil"
  [requisicao]
  (let [req-mapeada (clojure.edn/read-string requisicao)
        ttl (:time-to-live req-mapeada)]
    (if (> ttl 0) 
      (do
        (let [arquivo (procura-arquivo (:query req-mapeada))]
          (println (str "Solicitação para '" (:query req-mapeada) "' recebida. \n TTL é " ttl))
          (if arquivo
            (do (println "iniciar transferencia via tcp"))
            (do (println "Arquivo não encontrado, repassando solicitação")
                (repassa-busca req-mapeada)))
          )))))

