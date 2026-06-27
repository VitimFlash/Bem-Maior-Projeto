package game.bmm.service.evento.tipo;

import game.bmm.model.Decisao;
import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
public class DuplicataStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "DUPLICATA"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "✌️ Evento Duplicata! Após escolher o destino das suas moedas, " +
                "você pode duplicar seus tributos ou seu bem-pessoal desta rodada. " +
                "50% de chance de multiplicar por 2 ou dividir por 2. " +
                "Você pode optar por não duplicar nada!";
    }

    // Aplica duplicata nos tributos
    public int aplicarNaTributos(int moedasTributo) {
        return new Random().nextBoolean()
                ? moedasTributo * 2
                : moedasTributo / 2;
    }

    // Aplica duplicata no bem-pessoal
    public void aplicarNoBemPessoal(Jogador jogador) {
        int bemAtual = jogador.getBemPessoal();
        jogador.setBemPessoal(new Random().nextBoolean()
                ? bemAtual * 2
                : bemAtual / 2);
        jogadorRepository.save(jogador);
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Processado individualmente por jogador via JogoController
    }
}
