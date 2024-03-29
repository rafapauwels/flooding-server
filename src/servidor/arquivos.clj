(in-ns 'servidor.core)

(def lista-arquivos (atom '()))

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

(defn atualiza-arquivos!
  "Atualiza a lista atômica lista-arquivos com todos os arquivos (recursivamente) do diretório."
  [diretorio]
  (let [dire (clojure.java.io/file diretorio)]
    (swap! lista-arquivos 
           (fn [estado-atual] (map str (filter #(.isFile %) (file-seq dire)))))))

(defn loop-atualizacao-arquivos!
  "Atualiza a cada 10000ms a lista de arquivos com os dados do diretório"
  [diretorio]
  (let [running (atom true)]
    (future
      (while @running
        (do
          (Thread/sleep 10000)
          (println "Atualizando arquivos")
          (atualiza-arquivos! diretorio))))))
