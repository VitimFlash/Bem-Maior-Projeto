package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VenenoStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "VENENO"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        Jogador feiticeiro = jogadores.stream()
                .filter(j -> j.getId().toString().equals(evento.getJogadorAlvoId()))
                .findFirst().orElse(null);
        String nome = feiticeiro != null ? feiticeiro.getUsuario().getUsername() : "?";
        return "🧪 " + nome + " é o Feiticeiro desta rodada! " +
                "Ele escolherá um jogador para envenenar. " +
                "Se o jogador envenenado não colocar todas as 5 moedas nos tributos, " +
                "perderá 5 moedas do bem-pessoal!";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        if (alvo == null) return;

        // Verifica se o alvo colocou todas as moedas nos tributos
        // valorEfeito = 1 se o veneno foi ativado, 0 se não
        if (evento.getValorEfeito() == 1) {
            alvo.setBemPessoal(alvo.getBemPessoal() - 5);
            jogadorRepository.save(alvo);
            evento.setDescricao("🧪 O veneno foi ativado!");
        } else {
            evento.setDescricao("🧪 O veneno não foi ativado!");
        }
    }
}
