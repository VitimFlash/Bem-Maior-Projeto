package game.bmm.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "votacoes")
public class Votacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sala_id")
    private Sala sala;

    @ManyToOne
    @JoinColumn(name = "jogador_alvo_id")
    private Jogador jogadorAlvo;

    private int votosFavor;
    private int votosContra;
    private boolean concluida;
    private boolean eliminado;

    @OneToMany(mappedBy = "votacao", cascade = CascadeType.ALL)
    private List<VotoEliminacao> votos;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }

    public Jogador getJogadorAlvo() { return jogadorAlvo; }
    public void setJogadorAlvo(Jogador jogadorAlvo) { this.jogadorAlvo = jogadorAlvo; }

    public int getVotosFavor() { return votosFavor; }
    public void setVotosFavor(int votosFavor) { this.votosFavor = votosFavor; }

    public int getVotosContra() { return votosContra; }
    public void setVotosContra(int votosContra) { this.votosContra = votosContra; }

    public boolean isConcluida() { return concluida; }
    public void setConcluida(boolean concluida) { this.concluida = concluida; }

    public boolean isEliminado() { return eliminado; }
    public void setEliminado(boolean eliminado) { this.eliminado = eliminado; }

    public List<VotoEliminacao> getVotos() { return votos; }
    public void setVotos(List<VotoEliminacao> votos) { this.votos = votos; }
}