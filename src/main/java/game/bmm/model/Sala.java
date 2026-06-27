package game.bmm.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "salas")
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String codigo;

    private int maxJogadores;
    private int totalRodadas;      // maxJogadores * 2
    private int rodadaAtual;
    private int meta;              // int(maxJogadores * 5 * 2.4)

    private String status;

    @OneToMany(mappedBy = "sala", cascade = CascadeType.ALL)
    private List<Jogador> jogadores;

    @OneToMany(mappedBy = "sala", cascade = CascadeType.ALL)
    private List<Rodada> rodadas;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public int getMaxJogadores() { return maxJogadores; }
    public void setMaxJogadores(int maxJogadores) { this.maxJogadores = maxJogadores; }

    public int getTotalRodadas() { return totalRodadas; }
    public void setTotalRodadas(int totalRodadas) { this.totalRodadas = totalRodadas; }

    public int getRodadaAtual() { return rodadaAtual; }
    public void setRodadaAtual(int rodadaAtual) { this.rodadaAtual = rodadaAtual; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Jogador> getJogadores() { return jogadores; }
    public void setJogadores(List<Jogador> jogadores) { this.jogadores = jogadores; }

    public List<Rodada> getRodadas() { return rodadas; }
    public void setRodadas(List<Rodada> rodadas) { this.rodadas = rodadas; }
}