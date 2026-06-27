package game.bmm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tributos")
public class Tributo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "rodada_id")
    private Rodada rodada;

    private int totalArrecadado;
    private int totalDistribuido;
    private int porJogador;
    private int descartado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Rodada getRodada() { return rodada; }
    public void setRodada(Rodada rodada) { this.rodada = rodada; }

    public int getTotalArrecadado() { return totalArrecadado; }
    public void setTotalArrecadado(int totalArrecadado) { this.totalArrecadado = totalArrecadado; }

    public int getTotalDistribuido() { return totalDistribuido; }
    public void setTotalDistribuido(int totalDistribuido) { this.totalDistribuido = totalDistribuido; }

    public int getPorJogador() { return porJogador; }
    public void setPorJogador(int porJogador) { this.porJogador = porJogador; }

    public int getDescartado() { return descartado; }
    public void setDescartado(int descartado) { this.descartado = descartado; }
}