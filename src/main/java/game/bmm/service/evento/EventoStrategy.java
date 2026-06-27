package game.bmm.service.evento;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.model.Rodada;

import java.util.List;

public interface EventoStrategy {

    // Identificador único do tipo de evento
    String getTipo();

    // Executa o efeito do evento
    // jogadores = lista de jogadores ativos na sala
    // alvo = jogador afetado (pode ser null se não houver alvo específico)
    // executor = jogador que executa a ação (pode ser null)
    void executar(Evento evento, List<Jogador> jogadores,
                  Jogador alvo, Jogador executor);

    // Retorna true se este evento precisa de uma decisão do jogador
    boolean requerDecisao();

    // Descrição exibida ao jogador quando o evento acontece
    String gerarDescricao(Evento evento, List<Jogador> jogadores);
}