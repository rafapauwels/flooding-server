(in-ns 'servidor.core)

(defn recebe-mensagem-tcp
  [socket]
  (.readLine (io/reader socket)))

(defn envia-mensagem-tcp
  [socket mensagem]
  (let [writer (io/writer socket)]
    (.write writer mensagem)
    (.flush writer)))

(defn loop-recebimento-tcp
  "Loop que mantém o recebimento de requisições TCP"
  [porta funcao-handler]
  (let [executando (atom true)]
    (future
      (with-open [server-sock (ServerSocket. porta)]
        (while @executando
          (with-open [sock (.accept server-sock)]
            (let [mensagem-entrada (recebe-mensagem-tcp sock)
                  mensagem-saida (funcao-handler mensagem-entrada)]
              (println "Enviando arquivo (encode base64)")
              (send sock mensagem-saida))))))
    @executando))
