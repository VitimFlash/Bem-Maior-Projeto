package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TraicaoStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "TRAICAO"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🔪 Evento Traição! Um traidor oculto foi escolhido. " +
                "O traidor escolherá quem trair durante a fase de decisão. " +
                "A vítima terá que adivinhar quem a traiu. " +
                "Acertou? O traidor perde 3 moedas. Errou? A vítima perde 3 moedas!";
    }

    // Processa o resultado da adivinhação
    // acertou = true se a vítima adivinhou o traidor
    public void processarAdivinacao(Jogador traidor, Jogador vitima, boolean acertou) {
        if (acertou) {
            traidor.setBemPessoal(traidor.getBemPessoal() - 3);
            jogadorRepository.save(traidor);
            evento_descricao = "🔪 Traidor revelado! " +
                    traidor.getUsuario().getUsername() + " perdeu 3 moedas!";
        } else {
            vitima.setBemPessoal(vitima.getBemPessoal() - 3);
            jogadorRepository.save(vitima);
            evento_descricao = "🔪 Traição bem sucedida! A vítima perdeu 3 moedas.";
        }
    }

    private String evento_descricao = "";

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // valorEfeito: 1 = vítima acertou, 0 = vítima errou
        if (alvo == null || executor == null) return;
        processarAdivinacao(executor, alvo, evento.getValorEfeito() == 1);
        evento.setDescricao(evento_descricao);
    }
}
