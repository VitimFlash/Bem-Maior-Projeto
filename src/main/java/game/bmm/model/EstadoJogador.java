package game.bmm.model;

public class EstadoJogador {

    private String username;
    private int moedasDaRodada;     // 5 moedas (ou diferente se evento alterar)
    private int contaPessoal;       // acumulado público
    private int bemPessoal;         // acumulado privado (só ele vê, só na fase DECISAO)
    private boolean eliminado;
    private String fase;

    private String eventoDescricao;  // descrição do evento se afetar este jogador
    private boolean eventoRequerAcao; // se o jogador precisa tomar decisão extra

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getMoedasDaRodada() { return moedasDaRodada; }
    public void setMoedasDaRodada(int moedasDaRodada) { this.moedasDaRodada = moedasDaRodada; }

    public int getContaPessoal() { return contaPessoal; }
    public void setContaPessoal(int contaPessoal) { this.contaPessoal = contaPessoal; }

    public int getBemPessoal() { return bemPessoal; }
    public void setBemPessoal(int bemPessoal) { this.bemPessoal = bemPessoal; }

    public boolean isEliminado() { return eliminado; }
    public void setEliminado(boolean eliminado) { this.eliminado = eliminado; }

    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }

    public String getEventoDescricao() { return eventoDescricao; }
    public void setEventoDescricao(String eventoDescricao) { this.eventoDescricao = eventoDescricao; }

    public boolean isEventoRequerAcao() { return eventoRequerAcao; }
    public void setEventoRequerAcao(boolean eventoRequerAcao) { this.eventoRequerAcao = eventoRequerAcao; }
}