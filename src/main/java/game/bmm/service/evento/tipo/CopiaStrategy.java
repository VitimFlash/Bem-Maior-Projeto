package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CopiaStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "COPIA"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "📄 Evento Cópia! Após todos decidirem o destino das moedas, " +
                "você pode copiar o bem-pessoal de outro jogador. " +
                "Seu bem-pessoal ficará igual ao do jogador escolhido. " +
                "Você pode optar por não copiar ninguém!";
    }

    // Copia o bem-pessoal do alvo para o copiador
    public void copiar(Jogador copiador, Jogador alvo) {
        copiador.setBemPessoal(alvo.getBemPessoal());
        jogadorRepository.save(copiador);
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        if (alvo == null || executor == null) return;
        copiar(executor, alvo);
        evento.setDescricao("📄 " + executor.getUsuario().getUsername() +
                " copiou o bem-pessoal de alguém!");
    }
}