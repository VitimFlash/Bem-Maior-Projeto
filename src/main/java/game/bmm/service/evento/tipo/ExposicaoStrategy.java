package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExposicaoStrategy implements EventoStrategy {

    @Override
    public String getTipo() { return "EXPOSICAO"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        Jogador expositor = jogadores.stream()
                .filter(j -> j.getId().toString().equals(evento.getJogadorAlvoId()))
                .findFirst().orElse(null);
        String nome = expositor != null ? expositor.getUsuario().getUsername() : "?";
        return "🔍 " + nome + " é o Expositor desta rodada! " +
                "Ele poderá espiar o bem-pessoal de um jogador antes de decidir. " +
                "Ninguém saberá quem foi espionado.";
    }

    // Retorna o bem-pessoal do jogador espionado (só enviado ao expositor)
    public int espiar(Jogador alvo) {
        return alvo.getBemPessoal();
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Apenas informação — sem efeito no bem-pessoal
        evento.setDescricao("🔍 O Expositor espiou alguém nesta rodada.");
    }
}
