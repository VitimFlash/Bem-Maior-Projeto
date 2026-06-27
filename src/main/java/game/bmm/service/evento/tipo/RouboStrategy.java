package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouboStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "ROUBO"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "💰 Um jogador foi escolhido para roubar 3 moedas do bem-pessoal de alguém! " +
                "O ladrão pode optar por não roubar ninguém. " +
                "O roubo será revelado no fim da rodada.";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        if (alvo == null || executor == null) return;

        // Remove 3 moedas do bem-pessoal do alvo (pode ficar negativo)
        alvo.setBemPessoal(alvo.getBemPessoal() - 3);
        jogadorRepository.save(alvo);

        // Adiciona 3 moedas ao bem-pessoal do ladrão
        executor.setBemPessoal(executor.getBemPessoal() + 3);
        jogadorRepository.save(executor);

        evento.setDescricao("💰 " + executor.getUsuario().getUsername() + " roubou nesta rodada!");
    }
}
