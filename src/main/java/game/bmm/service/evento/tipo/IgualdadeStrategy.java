package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IgualdadeStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "IGUALDADE"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🟰 Evento Igualdade! Votem: devemos igualar todos os bem-pessoais? " +
                "Se a maioria disser SIM, o bem-pessoal de todos será somado " +
                "e dividido igualmente (sem números quebrados). " +
                "Se não, o jogo segue normalmente.";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // valorEfeito: 1 = maioria votou sim, 0 = maioria votou não
        if (evento.getValorEfeito() != 1) {
            evento.setDescricao("🟰 A igualdade foi rejeitada. Jogo segue normalmente.");
            return;
        }

        int total = jogadores.stream()
                .mapToInt(Jogador::getBemPessoal)
                .sum();
        int igualado = total / jogadores.size(); // sem números quebrados

        for (Jogador jogador : jogadores) {
            jogador.setBemPessoal(igualado);
            jogadorRepository.save(jogador);
        }

        evento.setDescricao("🟰 Igualdade aprovada!");
    }
}
