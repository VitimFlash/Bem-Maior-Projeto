package game.bmm.model;

import java.util.List;
import java.util.Map;

public class EstadoSala {

    private String codigoSala;
    private int rodadaAtual;
    private int totalRodadas;
    private int meta;
    private String fase;

    private String mensagem;           // ex: "Aguardando 2 jogadores decidirem..."
    private List<String> jaDecidiram;  // nomes de quem já enviou decisão (sem valores)
    private int totalTributosRodada;   // revelado após todos decidirem
    private int valorDistribuido;      // quanto cada um recebeu na conta-pessoal
    private boolean podeVotar;
    private int tempoDiscussao;
    private String jogadorEliminado;
    private List<String> jogadoresEliminados;
    private String vencedor;
    private boolean decisaoEventoAntes;
    private boolean decisaoEventoDepois;
    private Map<String, Integer> contasPessoais; // nome → conta-pessoal (público)
    private EventoInfo eventoAtualInfo;

    public EventoInfo getEventoAtualInfo() { return eventoAtualInfo; }
    public void setEventoAtualInfo(EventoInfo e) { this.eventoAtualInfo = e; }

    // Classe interna
    public static class EventoInfo {
        public String tipo;
        public String descricao;
        public boolean requerDecisao;
        public String jogadorAlvoId;
        public boolean souAlvo;
        public boolean souExecutor;
        public boolean souFeiticeiro;
        public boolean souParceiro;
        public boolean souExpositor;
        public boolean souTraidor;
        public boolean souVitima;
        public boolean souPortador;
    }

    // Revelado apenas no FIM:
    private Map<String, Integer> bensPessoais;   // nome → bem-pessoal (oculto durante jogo)
    private Map<String, Integer> totaisFinais;   // nome → conta + bem-pessoal


    public boolean isDecisaoEventoAntes() { return decisaoEventoAntes; }
    public void setDecisaoEventoAntes(boolean d) { this.decisaoEventoAntes = d; }
    public boolean isDecisaoEventoDepois() { return decisaoEventoDepois; }
    public void setDecisaoEventoDepois(boolean d) { this.decisaoEventoDepois = d; }

    public List<String> getJogadoresEliminados() { return jogadoresEliminados; }
    public void setJogadoresEliminados(List<String> j) { this.jogadoresEliminados = j; }

    public String getJogadorEliminado() { return jogadorEliminado; }
    public void setJogadorEliminado(String j) { this.jogadorEliminado = j; }

    public boolean isPodeVotar() { return podeVotar; }
    public void setPodeVotar(boolean podeVotar) { this.podeVotar = podeVotar; }

    public int getTempoDiscussao() { return tempoDiscussao; }
    public void setTempoDiscussao(int tempoDiscussao) { this.tempoDiscussao = tempoDiscussao; }

    public String getCodigoSala() { return codigoSala; }
    public void setCodigoSala(String codigoSala) { this.codigoSala = codigoSala; }

    public int getRodadaAtual() { return rodadaAtual; }
    public void setRodadaAtual(int rodadaAtual) { this.rodadaAtual = rodadaAtual; }

    public int getTotalRodadas() { return totalRodadas; }
    public void setTotalRodadas(int totalRodadas) { this.totalRodadas = totalRodadas; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public List<String> getJaDecidiram() { return jaDecidiram; }
    public void setJaDecidiram(List<String> jaDecidiram) { this.jaDecidiram = jaDecidiram; }

    public int getTotalTributosRodada() { return totalTributosRodada; }
    public void setTotalTributosRodada(int totalTributosRodada) { this.totalTributosRodada = totalTributosRodada; }

    public int getValorDistribuido() { return valorDistribuido; }
    public void setValorDistribuido(int valorDistribuido) { this.valorDistribuido = valorDistribuido; }

    public Map<String, Integer> getContasPessoais() { return contasPessoais; }
    public void setContasPessoais(Map<String, Integer> contasPessoais) { this.contasPessoais = contasPessoais; }

    public Map<String, Integer> getBensPessoais() { return bensPessoais; }
    public void setBensPessoais(Map<String, Integer> bensPessoais) { this.bensPessoais = bensPessoais; }

    public Map<String, Integer> getTotaisFinais() { return totaisFinais; }
    public void setTotaisFinais(Map<String, Integer> totaisFinais) { this.totaisFinais = totaisFinais; }

    public String getVencedor() { return vencedor; }
    public void setVencedor(String vencedor) { this.vencedor = vencedor; }
}