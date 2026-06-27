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
public class BombaRelogioStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    // Ticks ocultos entre 1 e 15
    public static int gerarTicks() {
        return new Random().nextInt(15) + 1;
    }

    @Override
    public String getTipo() { return "BOMBA_RELOGIO"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        Jogador portador = jogadores.stream()
                .filter(j -> j.getId().toString().equals(evento.getJogadorAlvoId()))
                .findFirst().orElse(null);
        String nome = portador != null ? portador.getUsuario().getUsername() : "?";
        return "💣 Evento Bomba-Relógio! " + nome + " recebeu a bomba! " +
                "Cada portador deve passar a bomba para outro jogador. " +
                "Quando explodir, o último portador perde 4 moedas do bem-pessoal!";
    }

    // Passa a bomba para o próximo jogador e decrementa os ticks
    // Retorna true se a bomba explodiu
    public boolean passarBomba(Evento evento, Jogador portadorAtual,
                               Jogador proximoPortador) {
        int ticksRestantes = evento.getValorEfeito();
        ticksRestantes--;

        if (ticksRestantes <= 0) {
            // Bomba explode no portador atual
            portadorAtual.setBemPessoal(portadorAtual.getBemPessoal() - 4);
            jogadorRepository.save(portadorAtual);
            evento.setDescricao("💣 BOOM! " +
                    portadorAtual.getUsuario().getUsername() +
                    " perdeu 4 moedas!");
            return true;
        }

        // Atualiza ticks e novo portador (oculto)
        evento.setValorEfeito(ticksRestantes);
        evento.setJogadorAlvoId(proximoPortador.getId().toString());
        return false;
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Processado via JogoController a cada passagem
    }
}
