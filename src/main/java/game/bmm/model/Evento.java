package game.bmm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "eventos")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;
    private String descricao;
    private boolean requerDecisao;
    private int valorEfeito;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public boolean isRequerDecisao() { return requerDecisao; }
    public void setRequerDecisao(boolean requerDecisao) { this.requerDecisao = requerDecisao; }

    public int getValorEfeito() { return valorEfeito; }
    public void setValorEfeito(int valorEfeito) { this.valorEfeito = valorEfeito; }

    public void setJogadorAlvoId(String string) {
    }

    public String getJogadorAlvoId() {
        return null;
    }
}