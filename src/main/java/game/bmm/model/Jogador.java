package game.bmm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "jogadores")
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "sala_id")
    private Sala sala;

    private int contaPessoal;
    private int bemPessoal;
    private boolean eliminado;
    private boolean ativo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Sala getSala() { return sala; }
    public void setSala(Sala sala) { this.sala = sala; }

    public int getContaPessoal() { return contaPessoal; }
    public void setContaPessoal(int contaPessoal) { this.contaPessoal = contaPessoal; }

    public int getBemPessoal() { return bemPessoal; }
    public void setBemPessoal(int bemPessoal) { this.bemPessoal = bemPessoal; }

    public boolean isEliminado() { return eliminado; }
    public void setEliminado(boolean eliminado) { this.eliminado = eliminado; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}