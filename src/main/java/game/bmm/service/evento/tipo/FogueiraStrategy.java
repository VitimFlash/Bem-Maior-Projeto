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
public class FogueiraStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "FOGUEIRA"; }

    @Override
    public boolean requerDecisao() { return false; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🔥 Evento Fogueira! Todo bem-pessoal colocado nesta rodada " +
                "será acumulado na fogueira e ficará oculto. " +
                "No final da rodada a fogueira apaga e um jogador aleatório " +
                "perderá todo o valor acumulado!";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // valorEfeito = total acumulado na fogueira
        int totalFogueira = evento.getValorEfeito();
        if (totalFogueira <= 0 || jogadores.isEmpty()) return;

        // Sorteia um jogador aleatório para perder
        Jogador azarado = jogadores.get(new Random().nextInt(jogadores.size()));
        azarado.setBemPessoal(azarado.getBemPessoal() - totalFogueira);
        jogadorRepository.save(azarado);

        evento.setDescricao("🔥 A fogueira apagou! " +
                azarado.getUsuario().getUsername() +
                " perdeu " + totalFogueira + " moedas!");
    }
}
