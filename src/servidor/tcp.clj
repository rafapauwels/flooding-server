(in-ns 'servidor.core)

(defn recebe-tcp
  [socket]
  (.readLine (io/reader socket)))

(defn envia-tcp
  [socket mensagem]
  (let [writer (io/writer socket)]
    (.write writer mensagem)
    (.flush writer)))

(defn loop-recebimento-tcp
  "Loop que mantém o recebimento de requisições TCP"
  [porta handler]
  (let [running (atom true)]
    (future
      (with-open [server-sock (ServerSocket. porta)]
        (while @running
          (with-open [sock (.accept server-sock)]
            (let [msg-in (recebe-tcp sock)
                  msg-out (handler msg-in)]
              (envia-tcp sock msg-out))))))
    running))

(defn arquivo->base64
  [caminho-do-arquivo]
  "testeb64")

(defn arquivo->bytes
  [caminho-do-arquivo]
  (let [arquivo (java.io.File. caminho-do-arquivo)
        arry (byte-array (.length arquivo))
        input-strm (java.io.FileInputStream. arquivo)]
    (.read input-strm arry)
    (.close input-strm)
    arry))
