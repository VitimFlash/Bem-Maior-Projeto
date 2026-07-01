package game.bmm.controller;

import game.bmm.model.*;
import game.bmm.repository.*;
import game.bmm.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@Controller
public class JogoController {

    @Autowired private JogoService jogoService;
    @Autowired private EventoService eventoService;
    @Autowired private SimpMessagingTemplate mensageiro;
    @Autowired private SalaRepository salaRepository;
    @Autowired private RodadaRepository rodadaRepository;
    @Autowired private JogadorRepository jogadorRepository;
    @Autowired private VotacaoService votacaoService;

    @RestController
    @RequestMapping("/jogo")
    class JogoRestController {

        @PostMapping("/iniciar-rodada")
        public ResponseEntity<?> iniciarRodada(@RequestBody Map<String, String> body) {
            try {
                String codigo = body.get("codigo");
                iniciarNovaRodada(codigo);
                return ResponseEntity.ok("Rodada iniciada!");
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @PostMapping("/evento/acao")
        public ResponseEntity<?> acaoEvento(@RequestBody Map<String, Object> body,
                                            Authentication auth) {
            try {
                String codigoSala = (String) body.get("codigoSala");
                String tipo = (String) body.get("tipo");
                String acao = (String) body.get("acao");
                String alvo = (String) body.get("alvo");
                String username = auth.getName();

                Map<String, Object> resultado = jogoService.processarAcaoEvento(
                        codigoSala, username, tipo, acao, alvo
                );

                // Notifica todos se necessário
                if (resultado.containsKey("notificarSala")) {
                    EstadoSala estado = jogoService.montarEstadoSala(codigoSala);
                    estado.setMensagem((String) resultado.get("mensagemSala"));
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, estado);
                }

                return ResponseEntity.ok(resultado);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        @GetMapping("/estado/{codigo}")
        public ResponseEntity<?> estadoAtual(@PathVariable String codigo,
                                             Authentication auth) {
            try {
                EstadoSala estadoSala = jogoService.montarEstadoSala(codigo);
                EstadoJogador estadoJogador = jogoService.montarEstadoJogador(
                        codigo, auth.getName()
                );
                return ResponseEntity.ok(Map.of(
                        "sala", estadoSala,
                        "jogador", estadoJogador
                ));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        @GetMapping("/resultado/{codigo}")
        public ResponseEntity<?> resultado(@PathVariable String codigo) {
            try {
                return ResponseEntity.ok(jogoService.montarEstadoFinal(codigo));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        @GetMapping("/historico/{codigo}")
        public ResponseEntity<?> historico(@PathVariable String codigo) {
            try {
                Sala sala = salaRepository.findByCodigo(codigo)
                        .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
                return ResponseEntity.ok(
                        rodadaRepository.findBySalaOrderByNumeroAsc(sala)
                );
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }
    }

    // Recebe decisão do jogador via WebSocket
    @MessageMapping("/jogo/{codigoSala}/decisao")
    public void receberDecisao(@DestinationVariable String codigoSala,
                               Map<String, Object> body,
                               Authentication auth) {
        String username = auth.getName();
        int moedasTributo = (int) body.get("moedasTributo");
        int moedasBemPessoal = (int) body.get("moedasBemPessoal");

        jogoService.registrarDecisao(codigoSala, username, moedasTributo, moedasBemPessoal);

        // Envia estado privado ao jogador
        EstadoJogador estadoJogador = jogoService.montarEstadoJogador(codigoSala, username);
        mensageiro.convertAndSendToUser(username, "/queue/estado-jogador", estadoJogador);

        // Atualiza lista de quem já decidiu para todos
        EstadoSala estadoSala = jogoService.montarEstadoSala(codigoSala);
        estadoSala.setFase("DECISAO");
        estadoSala.setMensagem(username + " já decidiu. Aguardando os demais...");
        mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoSala);

        // Se todos decidiram processa a rodada
        if (jogoService.todosDecidiram(codigoSala)) {
            processarRodada(codigoSala);
        }
    }

    // Processa rodada após todos decidirem
    private void processarRodada(String codigoSala) {
        new Thread(() -> {
            try {
                // 1 — Processa tributos
                Tributo tributo = jogoService.processarTributos(codigoSala);
                EstadoSala estadoRevelacao = jogoService.montarEstadoSala(codigoSala);
                estadoRevelacao.setFase("REVELACAO");
                estadoRevelacao.setMensagem(
                        "🏛️ Tributos desta rodada: " + tributo.getTotalArrecadado() +
                                " moedas arrecadadas! Cada jogador recebeu " +
                                tributo.getPorJogador() + " 🪙 na conta-pessoal. " +
                                tributo.getDescartado() + " moedas descartadas."
                );
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoRevelacao);

                // 2 — Pausa de 5 segundos para ver os tributos
                Thread.sleep(5000);

                // 3 — Avança ou finaliza
                boolean continua = jogoService.avancarRodada(codigoSala);

                if (!continua) {
                    EstadoSala estadoFinal = jogoService.montarEstadoFinal(codigoSala);
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoFinal);
                    return;
                }

                // 4 — Fase de discussão (30 segundos)
                iniciarFaseDiscussao(codigoSala);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // Inicia nova rodada com evento se necessário
    public void iniciarNovaRodada(String codigoSala) throws InterruptedException {
        try {
            Sala sala = salaRepository.findByCodigo(codigoSala)
                    .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));

            iniciarFaseDiscussao(codigoSala);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            System.err.println("Erro ao iniciar rodada: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private void iniciarFaseDiscussao(String codigoSala) throws InterruptedException {
        Sala sala = salaRepository.findByCodigo(codigoSala)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));

        boolean podeVotar = sala.getRodadaAtual() > sala.getTotalRodadas() / 2;

        EstadoSala estadoDiscussao = jogoService.montarEstadoSala(codigoSala);
        estadoDiscussao.setFase("DISCUSSAO");
        estadoDiscussao.setTempoDiscussao(30);
        estadoDiscussao.setPodeVotar(podeVotar);
        estadoDiscussao.setMensagem(
                podeVotar
                        ? "💬 Tempo de discussão! Você pode votar para eliminar alguém."
                        : "💬 Tempo de discussão! A votação estará disponível após a metade do jogo."
        );
        mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoDiscussao);

        // Aguarda 30 segundos
        Thread.sleep(30000);

        // Processa resultado da votação se puder votar
        if (podeVotar) {
            VotacaoService.ResultadoVotacao resultado =
                    votacaoService.processarResultado(codigoSala);

            EstadoSala estadoVotacao = jogoService.montarEstadoSala(codigoSala);
            estadoVotacao.setFase("RESULTADO_VOTACAO");
            estadoVotacao.setMensagem(resultado.getMensagem());
            if (resultado.isEliminado()) {
                estadoVotacao.setJogadorEliminado(resultado.getJogadorEliminado());
            }
            mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoVotacao);
            Thread.sleep(3000);
        }

        // Inicia evento e decisão
        iniciarEventoERodada(codigoSala);
    }

    private void iniciarEventoERodada(String codigoSala) throws InterruptedException {
        Sala sala = salaRepository.findByCodigo(codigoSala)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        // Cria rodada
        jogoService.iniciarRodadaERetornarEstado(codigoSala);

        Rodada rodadaAtiva = rodadaRepository
                .findBySalaAndStatus(sala, "AGUARDANDO_DECISOES")
                .orElse(null);

        Evento eventoSorteado = null;

        if (rodadaAtiva != null && eventoService.deveHaverEvento(sala.getRodadaAtual())) {
            eventoSorteado = eventoService.sortearEvento(rodadaAtiva, ativos);

            if (eventoSorteado != null) {
                rodadaAtiva.setEvento(eventoSorteado);
                rodadaRepository.save(rodadaAtiva);

                String descricao = eventoService.gerarDescricao(eventoSorteado, ativos);
                eventoSorteado.setDescricao(descricao);

                // Anuncia evento para todos
                EstadoSala estadoEvento = jogoService.montarEstadoSala(codigoSala);
                estadoEvento.setFase("EVENTO");
                estadoEvento.setMensagem("⚡ " + eventoSorteado.getTipo());

                // Envia estado personalizado para cada jogador
                for (Jogador jogador : ativos) {
                    String username = jogador.getUsuario().getUsername();
                    EstadoSala estadoPersonalizado =
                            jogoService.montarEstadoSala(codigoSala);
                    estadoPersonalizado.setFase("EVENTO");
                    estadoPersonalizado.setMensagem("⚡ " + eventoSorteado.getTipo());

                    EstadoSala.EventoInfo info = jogoService
                            .montarInfoEventoParaJogador(eventoSorteado, username, sala);
                    estadoPersonalizado.setEventoAtualInfo(info);

                    mensageiro.convertAndSendToUser(
                            username,
                            "/queue/estado-jogador-evento",
                            estadoPersonalizado
                    );
                }

                // Aguarda 8 segundos de animação
                Thread.sleep(8000);

                // Se evento decide ANTES das moedas, envia fase de decisão do evento
                if (jogoService.eventoDecideAntesDasMoedas(eventoSorteado.getTipo())) {
                    for (Jogador jogador : ativos) {
                        String username = jogador.getUsuario().getUsername();
                        EstadoSala estadoDecisaoEvento =
                                jogoService.montarEstadoSala(codigoSala);
                        estadoDecisaoEvento.setFase("DECISAO_EVENTO_ANTES");
                        estadoDecisaoEvento.setDecisaoEventoAntes(true);

                        EstadoSala.EventoInfo info = jogoService
                                .montarInfoEventoParaJogador(eventoSorteado, username, sala);
                        estadoDecisaoEvento.setEventoAtualInfo(info);

                        mensageiro.convertAndSendToUser(
                                username,
                                "/queue/estado-jogador-evento",
                                estadoDecisaoEvento
                        );
                    }
                    // Aguarda 30 segundos para decisão do evento
                    Thread.sleep(30000);
                }
            }
        }

        // Envia fase de decisão das moedas
        EstadoSala estadoDecisao = jogoService.montarEstadoSala(codigoSala);
        estadoDecisao.setFase("DECISAO");
        estadoDecisao.setMensagem("Rodada " + sala.getRodadaAtual() +
                " iniciada! Faça sua escolha.");

        // Se tem evento que decide DEPOIS, informa o frontend
        if (eventoSorteado != null &&
                !jogoService.eventoDecideAntesDasMoedas(eventoSorteado.getTipo())) {
            estadoDecisao.setDecisaoEventoDepois(true);
            EstadoSala.EventoInfo info = new EstadoSala.EventoInfo();
            info.tipo = eventoSorteado.getTipo();
            info.requerDecisao = eventoSorteado.isRequerDecisao();
            estadoDecisao.setEventoAtualInfo(info);
        }

        mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoDecisao);
    }
}