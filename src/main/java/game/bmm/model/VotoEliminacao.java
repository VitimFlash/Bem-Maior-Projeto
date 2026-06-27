package game.bmm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "votos_eliminacao")
public class VotoEliminacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "votacao_id")
    private Votacao votacao;

    @ManyToOne
    @JoinColumn(name = "jogador_id")
    private Jogador jogador;

    private boolean favor;

    private String alvoUsername;

    public String getAlvoUsername() { return alvoUsername; }
    public void setAlvoUsername(String alvoUsername) { this.alvoUsername = alvoUsername; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Votacao getVotacao() { return votacao; }
    public void setVotacao(Votacao votacao) { this.votacao = votacao; }

    public Jogador getJogador() { return jogador; }
    public void setJogador(Jogador jogador) { this.jogador = jogador; }

    public boolean isFavor() { return favor; }
    public void setFavor(boolean favor) { this.favor = favor; }
}