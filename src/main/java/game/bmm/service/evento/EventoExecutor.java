package game.bmm.service.evento;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class EventoExecutor {

    private final Map<String, EventoStrategy> strategies = new HashMap<>();

    @Autowired
    public EventoExecutor(List<EventoStrategy> listaStrategies) {
        for (EventoStrategy strategy : listaStrategies) {
            strategies.put(strategy.getTipo(), strategy);
        }
    }

    // Executa o evento correto baseado no tipo
    public void executar(Evento evento, List<Jogador> jogadores,
                         Jogador alvo, Jogador executor) {
        EventoStrategy strategy = strategies.get(evento.getTipo());
        if (strategy == null) {
            throw new RuntimeException("Tipo de evento desconhecido: " + evento.getTipo());
        }
        strategy.executar(evento, jogadores, alvo, executor);
    }

    // Sorteia um tipo aleatório dentre os disponíveis (pode repetir)
    public String sortearTipo() {
        List<String> tipos = strategies.keySet().stream().toList();
        if (tipos.isEmpty()) return null;
        return tipos.get(new Random().nextInt(tipos.size()));
    }

    // Verifica se um tipo de evento requer decisão do jogador
    public boolean requerDecisao(String tipo) {
        EventoStrategy strategy = strategies.get(tipo);
        return strategy != null && strategy.requerDecisao();
    }

    // Verifica se um tipo existe
    public boolean tipoExiste(String tipo) {
        return strategies.containsKey(tipo);
    }

    // Lista todos os tipos disponíveis
    public List<String> listarTipos() {
        return strategies.keySet().stream().toList();
    }
}