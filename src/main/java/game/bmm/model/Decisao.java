package game.bmm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "decisoes")
public class Decisao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "jogador_id")
    private Jogador jogador;

    @ManyToOne
    @JoinColumn(name = "rodada_id")
    private Rodada rodada;

    private int moedasTributo;
    private int moedasBemPessoal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Jogador getJogador() { return jogador; }
    public void setJogador(Jogador jogador) { this.jogador = jogador; }

    public Rodada getRodada() { return rodada; }
    public void setRodada(Rodada rodada) { this.rodada = rodada; }

    public int getMoedasTributo() { return moedasTributo; }
    public void setMoedasTributo(int moedasTributo) { this.moedasTributo = moedasTributo; }

    public int getMoedasBemPessoal() { return moedasBemPessoal; }
    public void setMoedasBemPessoal(int moedasBemPessoal) { this.moedasBemPessoal = moedasBemPessoal; }
}