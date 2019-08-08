(in-ns 'servidor.core)

(def lista-requisicoes (atom '()))

(defn enviar-query
  "Envia uma requisição através de um socket para o alvo, definido pelo endereço e porta. Caso a mensagem ultrapasse 512 bytes
    ela será truncada"
  [^DatagramSocket socket query alvo]
  (let [payload (.getBytes (str query))
        tamanho-requisicao (min (alength payload) 512)
        endereco (InetSocketAddress. (:endereco-ip alvo) (:porta alvo))
        pacote (DatagramPacket. payload tamanho-requisicao endereco)]
    (.send socket pacote)))

(defn diminui-ttl
  "Diminui TTL da requisição por 1"
  [requisicao]
  (assoc requisicao 
         :time-to-live (dec (:time-to-live requisicao))))

(defn repassa-busca
  "Repassa a busca modificando seu TTL"
  [requisicao socket]
  (let [servidor-alvo (escolhe-servidor-alvo)]
    (println (str "Repassando query " (:query requisicao) " para " (:endereco-ip servidor-alvo)))
    (enviar-query socket
                  (str (diminui-ttl requisicao))
                  servidor-alvo)))

(defn constroi-resposta-query
  [arquivo]
  (zipmap [:endereco-tcp :caminho-arquivo]
          [(.getHostAddress (InetAddress/getLocalHost))
           arquivo]))

(defn enviar-resposta-query
  "Envia a reposta da query ao cliente, informando rota do arquivo e endereço para TCP"
    [^DatagramSocket socket arquivo cliente]
  (enviar-query socket
                (constroi-resposta-query arquivo)
                (zipmap [:endereco-ip :porta] 
                        [cliente 9442])))

(defn aviso-para-transferencia
  "Avisa o cliente que por onde será feita a transferência via UDP"
  [requisicao arquivo socket]
  (let [cliente (:endereco-origem requisicao)]
    (println (str "Enviando endereço tcp (via udp) para " cliente ", arquivo " arquivo))
    (enviar-resposta-query socket arquivo cliente))
  )

(defn requisicao-valida?!
  [requisicao]
  (let [req (zipmap [:endereco-origem :query]
                    [(:endereco-origem requisicao) (:query requisicao)])]
    (if (= 0 (count 
              (filter #(= req %) @lista-requisicoes)))
      (swap! lista-requisicoes conj req)
      nil)))

(defn trata-requisicao-de-busca
  "Recebe requisicao. Se o TTL > 0 inicia transferência tcp ou repassa requisição, do contrário retorna nil"
  [requisicao socket]
  (println (str "Mensagem " requisicao " recebida no socket"))
  (let [req-mapeada (clojure.edn/read-string requisicao)
        ttl (:time-to-live req-mapeada)]
    (if (> ttl 0)
      (if (requisicao-valida?! req-mapeada)
          (do
            (let [arquivo (procura-arquivo (:query req-mapeada))]
              (println (str "Solicitação para '" (:query req-mapeada) "' recebida. TTL igual a " ttl))
              (if arquivo
                (do (println "Arquivo encontrado, iniciando processo de transferência")
                    (aviso-para-transferencia req-mapeada arquivo socket))
                (do (println "Arquivo não encontrado, repassando solicitação")
                    (repassa-busca req-mapeada socket)))
              ))
          (println "Requisição duplicada"))
      (println "Time to live ZERO"))))

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
