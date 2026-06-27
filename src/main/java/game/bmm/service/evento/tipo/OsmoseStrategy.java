package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OsmoseStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "OSMOSE"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🧂 Evento Osmose! Após decidir o destino das moedas, " +
                "você pode desafiar outro jogador para um duelo. " +
                "Quem tiver mais moedas no bem-pessoal rouba 3 moedas do outro. " +
                "Empate = nada acontece. Só você saberá o resultado do seu duelo!";
    }

    // Executa um duelo entre dois jogadores
    // Retorna: 1 se desafiante venceu, -1 se perdeu, 0 se empate
    public int duelar(Jogador desafiante, Jogador oponente) {
        if (desafiante.getBemPessoal() > oponente.getBemPessoal()) {
            desafiante.setBemPessoal(desafiante.getBemPessoal() + 3);
            oponente.setBemPessoal(oponente.getBemPessoal() - 3);
            jogadorRepository.save(desafiante);
            jogadorRepository.save(oponente);
            return 1;
        } else if (desafiante.getBemPessoal() < oponente.getBemPessoal()) {
            desafiante.setBemPessoal(desafiante.getBemPessoal() - 3);
            oponente.setBemPessoal(oponente.getBemPessoal() + 3);
            jogadorRepository.save(desafiante);
            jogadorRepository.save(oponente);
            return -1;
        }
        return 0; // empate
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Processado individualmente via JogoController
    }
}
