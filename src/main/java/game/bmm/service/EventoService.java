package game.bmm.service;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.model.Rodada;
import game.bmm.repository.EventoRepository;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class EventoService {

    @Autowired private EventoRepository eventoRepository;
    @Autowired private JogadorRepository jogadorRepository;
    @Autowired private EventoExecutor eventoExecutor;

    // Eventos só acontecem a partir desta rodada
    private static final int RODADA_MINIMA_EVENTO = 3;

    // Sorteia e cria um evento para a rodada (retorna null se for antes da 3ª rodada)
    public Evento sortearEvento(Rodada rodada, List<Jogador> jogadoresAtivos) {

        // Sem evento nas primeiras 2 rodadas
        if (rodada.getNumero() < RODADA_MINIMA_EVENTO) {
            return null;
        }

        // A partir da 3ª rodada, sempre acontece um evento
        String tipo = eventoExecutor.sortearTipo();
        if (tipo == null) return null;

        // Sorteia um alvo aleatório entre os jogadores ativos
        Jogador alvo = jogadoresAtivos.get(
                new Random().nextInt(jogadoresAtivos.size())
        );

        Evento evento = new Evento();
        evento.setTipo(tipo);
        evento.setRequerDecisao(eventoExecutor.requerDecisao(tipo));
        evento.setJogadorAlvoId(alvo.getId().toString());
        evento.setValorEfeito(0); // definido por cada strategy
        return eventoRepository.save(evento);
    }

    // Executa o evento da rodada
    public void executarEvento(Evento evento, List<Jogador> jogadores,
                               Jogador alvo, Jogador executor) {
        eventoExecutor.executar(evento, jogadores, alvo, executor);
    }

    // Gera a descrição do evento para exibir na tela
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        return evento.getDescricao() != null
                ? evento.getDescricao()
                : "Um evento misterioso aconteceu!";
    }

    // Verifica se a rodada atual deve ter evento
    public boolean deveHaverEvento(int rodadaAtual) {
        return rodadaAtual >= RODADA_MINIMA_EVENTO;
    }
}