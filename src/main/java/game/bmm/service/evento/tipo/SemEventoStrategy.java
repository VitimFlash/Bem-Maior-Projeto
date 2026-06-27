package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

// Este é um evento placeholder — serve como exemplo para criar novos eventos
// Para criar um novo evento:
// 1. Copie este arquivo
// 2. Mude o getTipo() para o nome do seu evento
// 3. Implemente a lógica no método executar()
// 4. Adicione @Component para o Spring registrar automaticamente

@Component
public class SemEventoStrategy implements EventoStrategy {

    @Override
    public String getTipo() {
        return "SEM_EVENTO"; // identificador único
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Nenhuma ação — evento neutro
    }

    @Override
    public boolean requerDecisao() {
        return false;
    }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "Nada aconteceu nesta rodada.";
    }
}
