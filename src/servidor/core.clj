(ns servidor.core
  (:gen-class))
(require '[clojure.java.io :as io]
         '[clojure.data.codec.base64 :as b64])
(import '[java.net DatagramSocket
          DatagramPacket
          InetSocketAddress
          InetAddress
          ServerSocket])

(load "arquivos")
(load "tcp")
(load "servidores")
(load "udp")

(defn -main
  "Ponto de entrada para 'lein run'"
  [& args]
  (println "Iniciando servidor...")
  (let [porta 9443
        socket-udp (DatagramSocket. porta)]
    (loop-atualizacao-arquivos! "/home/pauwels/Documents")
    (loop-recebimento-requisicoes-de-busca socket-udp trata-requisicao-de-busca)
    (loop-recebimento-tcp porta arquivo->base64)))
