package game.bmm.service;

import game.bmm.model.Evento;
import game.bmm.model.Jogador;
import game.bmm.model.Rodada;
import game.bmm.repository.EventoRepository;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.evento.EventoExecutor;
import game.bmm.service.evento.tipo.BombaRelogioStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
        if (rodada.getNumero() < RODADA_MINIMA_EVENTO) return null;

        String tipo = eventoExecutor.sortearTipo();
        if (tipo == null) return null;

        Evento evento = new Evento();
        evento.setTipo(tipo);
        evento.setRequerDecisao(eventoExecutor.requerDecisao(tipo));
        evento.setValorEfeito(0);

        // Define alvo baseado no tipo do evento
        Jogador alvoAleatorio = jogadoresAtivos.get(
                new Random().nextInt(jogadoresAtivos.size())
        );

        switch (tipo) {
            case "ROUBO" ->
                // Alvo = ladrão
                    evento.setJogadorAlvoId(alvoAleatorio.getId().toString());
            case "VENENO" ->
                // Alvo = feiticeiro
                    evento.setJogadorAlvoId(alvoAleatorio.getId().toString());
            case "EXPOSICAO" ->
                // Alvo = expositor
                    evento.setJogadorAlvoId(alvoAleatorio.getId().toString());
            case "TRAICAO" ->
                // Alvo = traidor
                    evento.setJogadorAlvoId(alvoAleatorio.getId().toString());
            case "BOMBA_RELOGIO" -> {
                // Alvo = portador inicial + ticks ocultos
                evento.setJogadorAlvoId(alvoAleatorio.getId().toString());
                evento.setValorEfeito(BombaRelogioStrategy.gerarTicks());
            }
            case "PARCEIROS" -> {
                // Dois parceiros aleatórios
                List<Jogador> embaralhados = new ArrayList<>(jogadoresAtivos);
                Collections.shuffle(embaralhados);
                Jogador p1 = embaralhados.get(0);
                Jogador p2 = embaralhados.get(1);
                evento.setJogadorAlvoId(p1.getId() + "," + p2.getId());
            }
            // Roleta, Duplicata, Osmose, Copia, Igualdade, Liderança,
            // Bolha, Fogueira — sem alvo específico
            default -> evento.setJogadorAlvoId(null);
        }

        return eventoRepository.save(evento);
    }

    // Executa o evento da rodada
    public void executarEvento(Evento evento, List<Jogador> jogadores,
                               Jogador alvo, Jogador executor) {
        eventoExecutor.executar(evento, jogadores, alvo, executor);
    }

    // Gera a descrição do evento para exibir na tela
    public String gerarDescricao(Evento evento, List<Jogador> jogadores) {
        String nomeAlvo = jogadores.stream()
                .filter(j -> j.getId().toString().equals(evento.getJogadorAlvoId()))
                .map(j -> j.getUsuario().getUsername())
                .findFirst().orElse("um jogador");

        return switch (evento.getTipo()) {
            case "ROUBO" -> "💰 " + nomeAlvo + " foi escolhido como Ladrão! " +
                    "Ele pode roubar 3 moedas do bem-pessoal de alguém. " +
                    "O ladrão pode optar por não roubar ninguém. " +
                    "O roubo será revelado no fim da rodada.";
            case "VENENO" -> "🧪 Um Feiticeiro foi escolhido! " +
                    "Ele irá selecionar um jogador para envenenar. " +
                    "Se o jogador envenenado não colocar todas as 5 moedas nos tributos, " +
                    "perderá 5 moedas do bem-pessoal!";
            case "PARCEIROS" -> "🤝 Dois jogadores foram escolhidos como Parceiros! " +
                    "Cada um decide: Enganar ou Compartilhar. " +
                    "Ambos compartilham → bem-pessoal ×1.5. " +
                    "Um engana → enganador ×2. Ambos enganam → nada acontece.";
            case "ROLETA" -> "🎰 Evento Roleta! Cada jogador pode girar até 3 vezes. " +
                    "Cair em 1 ou 8 → +5 moedas no bem-pessoal. " +
                    "Qualquer outro número → -5 moedas. Você pode optar por não girar!";
            case "DUPLICATA" -> "✌️ Evento Duplicata! Após escolher o destino das moedas, " +
                    "você pode duplicar seus tributos ou seu bem-pessoal. " +
                    "50% de chance de multiplicar por 2 ou dividir por 2. " +
                    "Você pode optar por não duplicar nada!";
            case "EXPOSICAO" -> "🔍 " + nomeAlvo + " é o Expositor desta rodada! " +
                    "Ele poderá espiar o bem-pessoal de qualquer jogador antes de decidir. " +
                    "Ninguém saberá quem foi espionado.";
            case "LIDERANCA" -> "👑 Evento Liderança! Votem em quem será o Líder. " +
                    "O Líder receberá todas as moedas e decidirá o destino de cada uma: " +
                    "quanto vai para os tributos e quanto vai para o bem-pessoal de cada jogador.";
            case "OSMOSE" -> "🧂 Evento Osmose! Após decidir o destino das moedas, " +
                    "você pode desafiar outro jogador para um duelo. " +
                    "Quem tiver mais bem-pessoal rouba 3 moedas do outro. " +
                    "Empate = nada acontece. Só você saberá o resultado!";
            case "TRAICAO" -> "🔪 Evento Traição! Um traidor oculto foi escolhido. " +
                    "O traidor escolhe quem trair. A vítima tenta adivinhar quem a traiu. " +
                    "Acertou → traidor perde 3 moedas. Errou → vítima perde 3 moedas!";
            case "COPIA" -> "📄 Evento Cópia! Após todos decidirem, " +
                    "você pode copiar o bem-pessoal de outro jogador. " +
                    "Seu bem-pessoal ficará igual ao do jogador escolhido. " +
                    "Você pode optar por não copiar ninguém!";
            case "BOLHA" -> "🫧 Evento Bolha! Todo bem-pessoal colocado nesta rodada " +
                    "será acumulado na bolha e ficará oculto. " +
                    "No final a bolha estoura e um jogador aleatório recebe tudo!";
            case "BOMBA_RELOGIO" -> "💣 Evento Bomba-Relógio! " + nomeAlvo +
                    " recebeu a bomba! Cada portador deve passá-la para outro jogador. " +
                    "Quando explodir, o último portador perde 4 moedas do bem-pessoal!";
            case "IGUALDADE" -> "🟰 Evento Igualdade! Votem: devemos igualar todos os " +
                    "bem-pessoais? Se a maioria disser SIM, o bem-pessoal de todos será " +
                    "somado e dividido igualmente. Se não, o jogo segue normalmente.";
            case "FOGUEIRA" -> "🔥 Evento Fogueira! Todo bem-pessoal colocado nesta rodada " +
                    "será acumulado na fogueira e ficará oculto. " +
                    "No final a fogueira apaga e um jogador aleatório perde tudo acumulado!";
            default -> "⚡ Um evento especial aconteceu nesta rodada!";
        };
    }

    // Verifica se a rodada atual deve ter evento
    public boolean deveHaverEvento(int rodadaAtual) {
        return rodadaAtual >= RODADA_MINIMA_EVENTO;
    }
}