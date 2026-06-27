package game.bmm.service.evento.tipo;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LiderancaStrategy implements EventoStrategy {

    @Autowired private JogadorRepository jogadorRepository;

    @Override
    public String getTipo() { return "LIDERANCA"; }

    @Override
    public boolean requerDecisao() { return true; }

    @Override
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return "👑 Evento Liderança! Votem em quem será o Líder desta rodada. " +
                "O Líder receberá todas as moedas e decidirá o destino de cada uma: " +
                "quanto vai para os tributos e quanto vai para o bem-pessoal de cada jogador.";
    }

    // Aplica a distribuição decidida pelo líder
    // distribuicao: Map<username, Map<"tributo"|"bemPessoal", valor>>
    public void aplicarDistribuicao(List<Jogador> jogadores,
                                    Map<String, Map<String, Integer>> distribuicao) {
        for (Jogador jogador : jogadores) {
            String username = jogador.getUsuario().getUsername();
            if (distribuicao.containsKey(username)) {
                Map<String, Integer> valores = distribuicao.get(username);
                int bemPessoal = valores.getOrDefault("bemPessoal", 0);
                jogador.setBemPessoal(jogador.getBemPessoal() + bemPessoal);
                jogadorRepository.save(jogador);
            }
        }
    }

    @Override
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        // Processado via JogoController após o líder enviar a distribuição
        evento.setDescricao("👑 O Líder distribuiu todas as moedas desta rodada!");
    }
}
