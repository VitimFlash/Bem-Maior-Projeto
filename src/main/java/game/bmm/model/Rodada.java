package game.bmm.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "rodadas")
public class Rodada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sala_id")
    private Sala sala;

    private int numero;
    private int totalTributos;
    private int valorDistribuido;
    private int moedasDescartadas;

    private String status;
    // "AGUARDANDO_DECISOES", "PROCESSANDO", "CONCLUIDA"

    @OneToOne
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @OneToMany(mappedBy = "rodada", cascade = CascadeType.ALL)
    private List<Decisao> decisoes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public int getTotalTributos() { return totalTributos; }
    public void setTotalTributos(int totalTributos) { this.totalTributos = totalTributos; }

    public int getValorDistribuido() { return valorDistribuido; }
    public void setValorDistribuido(int valorDistribuido) { this.valorDistribuido = valorDistribuido; }

    public int getMoedasDescartadas() { return moedasDescartadas; }
    public void setMoedasDescartadas(int moedasDescartadas) { this.moedasDescartadas = moedasDescartadas; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public List<Decisao> getDecisoes() { return decisoes; }
    public void setDecisoes(List<Decisao> decisoes) { this.decisoes = decisoes; }
}