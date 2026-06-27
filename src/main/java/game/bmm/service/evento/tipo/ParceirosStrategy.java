package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParceirosStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "PARCEIROS"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        // jogadorAlvoId guarda "id1,id2" dos dois parceiros
        String[] ids = evento.getJogadorAlvoId().split(",");
        String p1 = jogadores.stream()
                .filter(j -> j.getId().toString().equals(ids[0]))
                .map(j -> j.getUsuario().getUsername())
                .findFirst().orElse("?");
        String p2 = ids.length > 1 ? jogadores.stream()
                .filter(j -> j.getId().toString().equals(ids[1]))
                .map(j -> j.getUsuario().getUsername())
                .findFirst().orElse("?") : "?";
        return "🤝 " + p1 + " e " + p2 + " são os Parceiros desta rodada! " +
                "Cada um escolhe: Enganar ou Compartilhar. " +
                "Ambos compartilham → bem-pessoal ×1.5. " +
                "Um engana → enganador ×2, outro não ganha nada.";
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // valorEfeito: 0=ambos enganam, 1=ambos compartilham,
        // 2=executor engana alvo, 3=alvo engana executor
        if (alvo == null || executor == null) return;

        switch (evento.getValorEfeito()) {
            case 0 -> evento.setDescricao("🤝 Ambos enganaram. Nada aconteceu.");
            case 1 -> {
                // Ambos compartilham → ×1.5
                executor.setBemPessoal((int)(executor.getBemPessoal() * 1.5));
                alvo.setBemPessoal((int)(alvo.getBemPessoal() * 1.5));
                jogadorRepository.save(executor);
                jogadorRepository.save(alvo);
                evento.setDescricao("🤝 Parceria bem sucedida! Ambos ganharam ×1.5!");
            }
            case 2 -> {
                // Executor engana alvo
                executor.setBemPessoal(executor.getBemPessoal() * 2);
                jogadorRepository.save(executor);
                evento.setDescricao("💔 Parceria quebrada! " +
                        executor.getUsuario().getUsername() + " enganou o parceiro!");
            }
            case 3 -> {
                // Alvo engana executor
                alvo.setBemPessoal(alvo.getBemPessoal() * 2);
                jogadorRepository.save(alvo);
                evento.setDescricao("💔 Parceria quebrada! " +
                        alvo.getUsuario().getUsername() + " enganou o parceiro!");
            }
        }
    }
}
