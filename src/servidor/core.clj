(ns servidor.core)
(require '[clojure.java.io :as io])
(import '[java.net DatagramSocket
          DatagramPacket
          InetSocketAddress
          InetAddress
          ServerSocket])

(def alvos [{:endereco-ip "localhost" :porta 9443}]) ; Usar peers da cloud (slurp http://cloud)

(defn recebe-requisicao
  "Bloqueante até receber uma requesição UDP através do socket especificado. Passa o payload adiante"
  [^DatagramSocket socket]
  (let [buffer (byte-array 512)
        pacote (DatagramPacket. buffer 512)]
    (.receive socket pacote)
    (String. (.getData pacote)
             0 (.getLength pacote))))

(defn loop-recebimento-requisicoes-de-busca
  "Recebe uma requisição pelo socket esquecificado e aplica a função f sobre a mensagem"
  [socket f]
  (future (while true (f (recebe-requisicao socket) socket))))

(defn enviar-query
  "Envia uma requisição através de um socket para o alvo, definido pelo endereço e porta. Caso a mensagem ultrapasse 512 bytes
    ela será truncada"
  [^DatagramSocket socket query alvo]
  (let [payload (.getBytes (str query))
        tamanho-requisicao (min (alength payload) 512)
        endereco (InetSocketAddress. (:endereco-ip alvo) (:porta alvo))
        pacote (DatagramPacket. payload tamanho-requisicao endereco)]
    (.send socket pacote)))

(defn constroi-resposta-query
  [arquivo]
  (zipmap [:endereco-tcp :caminho-arquivo]
          [(.getHostAddress (InetAddress/getLocalHost))
           arquivo]))

(defn enviar-resposta-query
    [^DatagramSocket socket arquivo cliente]
  (enviar-query socket
                (constroi-resposta-query arquivo)
                (zipmap [:endereco-ip :porta] 
                        [cliente 9442])))

(def lista-arquivos (atom '()))

(defn atualiza-arquivos!
  "Atualiza a lista atômica lista-arquivos com todos os arquivos (recursivamente) do diretório."
  [diretorio]
  (let [dire (clojure.java.io/file diretorio)]
    (swap! lista-arquivos 
           (fn [estado-atual] (map str (filter #(.isFile %) (file-seq dire)))))))

(defn loop-atualizacao-arquivos!
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

(defn escolhe-servidor-alvo
  "Escolhe um alvo da lista de alvos aleatoriamente"
  [alvos]
  (rand-nth alvos))

(defn diminui-ttl
  "Diminui TTL da requisição por 1"
  [requisicao]
  (assoc requisicao 
         :time-to-live (dec (:time-to-live requisicao))))

(defn repassa-busca
  "Repassa a busca modificando seu TTL"
  [requisicao socket]
  (let [servidor-alvo (escolhe-servidor-alvo alvos)]
    (println (str "Repassando query " requisicao " para " servidor-alvo))
    (enviar-query socket
                  (str (diminui-ttl requisicao))
                  servidor-alvo)))

(defn aviso-para-transferencia
  "Avisa o cliente que por onde será feita a transferência via UDP"
  [requisicao arquivo socket]
  (let [cliente (:endereco-origem requisicao)]
    (enviar-resposta-query socket arquivo cliente))
  )

(defn trata-requisicao-de-busca
  "Recebe requisicao. Se o TTL > 0 inicia transferência tcp ou repassa requisição, do contrário retorna nil"
  [requisicao socket]
  (let [req-mapeada (clojure.edn/read-string requisicao)
        ttl (:time-to-live req-mapeada)]
    (if (> ttl 0) 
      (do
        (let [arquivo (procura-arquivo (:query req-mapeada))]
          (println (str "Solicitação para '" (:query req-mapeada) "' recebida.\nTTL igual a " ttl))
          (if arquivo
            (do (println "Arquivo encontrado, iniciando processo de transferência")
                (aviso-para-transferencia requisicao socket))
            (do (println "Arquivo não encontrado, repassando solicitação")
                (repassa-busca req-mapeada socket)))
          )))))

(defn recebe-mensagem-tcp
  [socket]
  (.readLine (io/reader socket)))

(defn envia-mensagem-tcp
  [socket mensagem]
  (let [writer (io/writer socket)]
    (.write writer mensagem)
    (.flush writer)))

(defn recebe-requisicao-tcp
  "Recebe requisições tcp pela porta especificada, processa o pedido de arquivo e transmite o arquivo em base64"
  [porta f]
  (with-open [server-sock (ServerSocket. porta)
              sock (.accept server-sock)]
    (let [mensagem-recebida (recebe-mensagem-tcp sock)
          mensagem-enviada (f mensagem-recebida)]
      (envia-mensagem-tcp sock mensagem-enviada))))

(defn loop-recebimento-tcp
  "Loop que mantém o recebimento de requisições TCP"
  [porta]
  (future (while true (recebe-requisicao-tcp 9445 #(.toUpperCase %)))))

(defn -main
  "Ponto de entrada para 'lein run'"
  [& args]
  (println "Iniciando servidor...")
  (let [porta 9443
        socket-udp (DatagramSocket. porta)]
    (loop-atualizacao-arquivos! "/home/pauwels/Documents/Clojure")
    (loop-recebimento-requisicoes-de-busca socket-udp trata-requisicao-de-busca)
    (loop-recebimento-tcp porta)))
