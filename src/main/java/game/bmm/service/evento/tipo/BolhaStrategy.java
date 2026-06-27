package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class BolhaStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "BOLHA"; }

    @Override
    public boolean requerDecisao() { return false; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🫧 Evento Bolha! Todo bem-pessoal colocado nesta rodada " +
                "será acumulado na bolha e ficará oculto. " +
                "No final da rodada a bolha estoura e um jogador aleatório " +
                "receberá todo o valor acumulado!";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // valorEfeito = total acumulado na bolha
        int totalBolha = evento.getValorEfeito();
        if (totalBolha <= 0 || jogadores.isEmpty()) return;

        // Sorteia um jogador aleatório para receber
        Jogador sortudo = jogadores.get(new Random().nextInt(jogadores.size()));
        sortudo.setBemPessoal(sortudo.getBemPessoal() + totalBolha);
        jogadorRepository.save(sortudo);

        evento.setDescricao("🫧 A bolha estourou! " +
                sortudo.getUsuario().getUsername() +
                " recebeu " + totalBolha + " moedas!");
    }
}
