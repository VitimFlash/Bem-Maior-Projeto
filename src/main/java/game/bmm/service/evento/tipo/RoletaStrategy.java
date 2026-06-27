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
public class RoletaStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "ROLETA"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "🎰 Evento Roleta! Cada jogador pode girar a roleta até 3 vezes. " +
                "Cair em 1 ou 8 → +5 moedas no bem-pessoal. " +
                "Qualquer outro número → -5 moedas no bem-pessoal. " +
                "Você pode optar por não girar!";
    }

    // Gira a roleta e aplica o efeito para um jogador
    public int girar(Jogador jogador) {
        int resultado = new Random().nextInt(8) + 1;
        if (resultado == 1 || resultado == 8) {
            jogador.setBemPessoal(jogador.getBemPessoal() + 5);
        } else {
            jogador.setBemPessoal(jogador.getBemPessoal() - 5);
        }
        jogadorRepository.save(jogador);
        return resultado;
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Processado individualmente por jogador via JogoController
    }
}