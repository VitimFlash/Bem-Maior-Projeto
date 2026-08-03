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
    @Autowired private DecisaoRepository decisaoRepository;

    @RestController
    @RequestMapping("/jogo")
    class JogoRestController {
        // Armazena votos de liderança por sala
        private final Map<String, Map<String, Integer>> votosLideranca = new java.util.concurrent.ConcurrentHashMap<>();
        // Armazena líder eleito por sala
        private final Map<String, String> liderEleito = new java.util.concurrent.ConcurrentHashMap<>();

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

        @PostMapping("/evento/lideranca/votar")
        public ResponseEntity<?> votarLideranca(@RequestBody Map<String, Object> body,
                                                Authentication auth) {
            try {
                String codigoSala = (String) body.get("codigoSala");
                String alvo = (String) body.get("alvo");
                String username = auth.getName();

                // Registra voto
                votosLideranca.computeIfAbsent(codigoSala,
                                k -> new java.util.concurrent.ConcurrentHashMap<>())
                        .merge(alvo, 1, Integer::sum);

                Map<String, Integer> votos = votosLideranca.get(codigoSala);

                // Notifica todos com placar atualizado
                MensagemLiderancaVotos msg = new MensagemLiderancaVotos();
                msg.votos = votos;
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, msg);

                // Verifica se todos votaram
                Sala sala = salaRepository.findByCodigo(codigoSala)
                        .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
                List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

                int totalVotos = votos.values().stream().mapToInt(Integer::intValue).sum();

                if (totalVotos >= ativos.size()) {
                    // Elege o líder
                    String lider = votos.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(ativos.get(0).getUsuario().getUsername());

                    liderEleito.put(codigoSala, lider);

                    // Notifica todos quem é o líder
                    MensagemLiderEleito msgLider = new MensagemLiderEleito();
                    msgLider.lider = lider;
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgLider);

                    // Envia tela de distribuição APENAS para o líder
                    EstadoSala estadoLider = jogoService.montarEstadoSala(codigoSala);
                    estadoLider.setFase("LIDERANCA_DISTRIBUIR");
                    estadoLider.setMensagem("👑 Você é o Líder! Distribua as moedas.");
                    mensageiro.convertAndSendToUser(lider,
                            "/queue/estado-jogador-evento", estadoLider);

                    // Limpa votos
                    votosLideranca.remove(codigoSala);
                }

                return ResponseEntity.ok(Map.of("votosAtuais", votos));
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        @PostMapping("/evento/lideranca/distribuir")
        public ResponseEntity<?> distribuirLideranca(@RequestBody Map<String, Object> body,
                                                     Authentication auth) {
            try {
                String codigoSala = (String) body.get("codigoSala");

                @SuppressWarnings("unchecked")
                Map<String, Map<String, Integer>> distribuicao =
                        (Map<String, Map<String, Integer>>) body.get("distribuicao");

                Sala sala = salaRepository.findByCodigo(codigoSala)
                        .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
                List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

                // Cria decisões para todos os jogadores baseadas na distribuição do líder
                Rodada rodada = rodadaRepository
                        .findBySalaAndStatus(sala, "AGUARDANDO_DECISOES")
                        .orElseThrow(() -> new RuntimeException("Rodada nao encontrada."));

                for (Jogador jogador : ativos) {
                    String username = jogador.getUsuario().getUsername();
                    if (distribuicao.containsKey(username)) {
                        Map<String, Integer> valores = distribuicao.get(username);
                        int tributo = valores.getOrDefault("tributo", 0);
                        int bem = valores.getOrDefault("bemPessoal", 0);

                        // Salva decisão do líder para este jogador
                        Decisao decisao = new Decisao();
                        decisao.setJogador(jogador);
                        decisao.setRodada(rodada);
                        decisao.setMoedasTributo(tributo);
                        decisao.setMoedasBemPessoal(bem);
                        decisaoRepository.save(decisao);

                        // Atualiza bem-pessoal
                        jogador.setBemPessoal(jogador.getBemPessoal() + bem);
                        jogadorRepository.save(jogador);
                    }
                }

                // Notifica todos
                EstadoSala estado = jogoService.montarEstadoSala(codigoSala);
                estado.setMensagem("👑 O Líder distribuiu as moedas desta rodada!");
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estado);

                liderEleito.remove(codigoSala);

                // Processa a rodada automaticamente
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        processarRodada(codigoSala);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                return ResponseEntity.ok("Distribuição aplicada!");
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
        try {
            String username = auth.getName();
            int moedasTributo = (int) body.get("moedasTributo");
            int moedasBemPessoal = (int) body.get("moedasBemPessoal");

            System.out.println("=== DECISAO RECEBIDA ===");
            System.out.println("Jogador: " + username);
            System.out.println("Tributo: " + moedasTributo + " Bem: " + moedasBemPessoal);

            jogoService.registrarDecisao(codigoSala, username, moedasTributo, moedasBemPessoal);
            System.out.println("Decisao registrada com sucesso!");

            boolean todos = jogoService.todosDecidiram(codigoSala);
            System.out.println("Todos decidiram: " + todos);

            EstadoSala estadoSala = jogoService.montarEstadoSala(codigoSala);
            estadoSala.setFase("DECISAO");
            estadoSala.setMensagem(username + " já decidiu. Aguardando os demais...");
            mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoSala);

            EstadoJogador estadoJogador = jogoService.montarEstadoJogador(codigoSala, username);
            mensageiro.convertAndSendToUser(username, "/queue/estado-jogador", estadoJogador);

            if (todos) {
                System.out.println("=== INICIANDO PROCESSAR RODADA ===");
                processarRodada(codigoSala);
            }
        } catch (Exception e) {
            System.err.println("=== ERRO EM receberDecisao ===");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    // Processa rodada após todos decidirem
    private void processarRodada(String codigoSala) {
        new Thread(() -> {
            try {
                System.out.println("=== THREAD processarRodada INICIADA ===");
                Tributo tributo = jogoService.processarTributos(codigoSala);
                System.out.println("Tributos OK: " + tributo.getTotalArrecadado());

                // 1 — Processa tributos
                System.out.println("Processando tributos...");
                System.out.println("Tributos processados: " + tributo.getTotalArrecadado());

                EstadoSala estadoRevelacao = jogoService.montarEstadoSala(codigoSala);
                estadoRevelacao.setFase("REVELACAO");
                estadoRevelacao.setMensagem(
                        "🏛️ Tributos: " + tributo.getTotalArrecadado() +
                                " moedas! Cada jogador recebeu " +
                                tributo.getPorJogador() + " 🪙. " +
                                tributo.getDescartado() + " descartadas."
                );
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoRevelacao);
                System.out.println("Revelação enviada!");

                // 2 — Pausa de 5 segundos
                Thread.sleep(5000);

                // 3 — Avança rodada
                System.out.println("Avançando rodada...");
                boolean continua = jogoService.avancarRodada(codigoSala);
                System.out.println("Continua: " + continua);

                if (!continua) {
                    System.out.println("Jogo finalizado!");
                    EstadoSala estadoFinal = jogoService.montarEstadoFinal(codigoSala);
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoFinal);
                    return;
                }

                // 4 — Inicia discussão da próxima rodada
                System.out.println("Iniciando discussão...");
                iniciarFaseDiscussao(codigoSala);

                // Após processarTributos, verifica evento especial
                Rodada rodadaConcluida = rodadaRepository
                        .findBySalaAndStatus(
                                salaRepository.findByCodigo(codigoSala).orElseThrow(),
                                "CONCLUIDA"
                        ).orElse(null);

                if (rodadaConcluida != null && rodadaConcluida.getEvento() != null) {
                    Evento ev = rodadaConcluida.getEvento();
                    Sala salaEv = salaRepository.findByCodigo(codigoSala).orElseThrow();
                    List<Jogador> ativosEv = jogadorRepository.findBySalaAndAtivoTrue(salaEv);

                    if ("BOLHA".equals(ev.getTipo()) || "FOGUEIRA".equals(ev.getTipo())) {
                        // Calcula total do bem-pessoal colocado nesta rodada
                        List<Decisao> decisoesEv = decisaoRepository.findByRodada(rodadaConcluida);
                        int totalBemPessoal = decisoesEv.stream()
                                .mapToInt(Decisao::getMoedasBemPessoal)
                                .sum();

                        ev.setValorEfeito(totalBemPessoal);

                        // Sorteia jogador
                        Jogador sorteado = ativosEv.get(
                                new java.util.Random().nextInt(ativosEv.size())
                        );

                        if ("BOLHA".equals(ev.getTipo())) {
                            sorteado.setBemPessoal(sorteado.getBemPessoal() + totalBemPessoal);
                            jogadorRepository.save(sorteado);

                            // Notifica resultado da bolha por 5 segundos
                            MensagemResultadoEvento msgBolha = new MensagemResultadoEvento();
                            msgBolha.nomeEvento = "BOLHA";
                            msgBolha.emoji = "🫧";
                            msgBolha.mensagem = "🫧 A bolha estourou! " +
                                    sorteado.getUsuario().getUsername() +
                                    " recebeu " + totalBemPessoal + " moedas no bem-pessoal!";
                            mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgBolha);
                            Thread.sleep(5000);

                        } else { // FOGUEIRA
                            sorteado.setBemPessoal(sorteado.getBemPessoal() - totalBemPessoal);
                            jogadorRepository.save(sorteado);

                            MensagemResultadoEvento msgFogueira = new MensagemResultadoEvento();
                            msgFogueira.nomeEvento = "FOGUEIRA";
                            msgFogueira.emoji = "🔥";
                            msgFogueira.mensagem = "🔥 A fogueira apagou! " +
                                    sorteado.getUsuario().getUsername() +
                                    " perdeu " + totalBemPessoal + " moedas do bem-pessoal!";
                            mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgFogueira);
                            Thread.sleep(5000);
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("=== ERRO EM processarRodada ===");
                System.err.println("Mensagem: " + e.getMessage());
                e.printStackTrace();
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

        boolean eventoSubstituiDecisao = eventoSorteado != null &&
                "LIDERANCA".equals(eventoSorteado.getTipo());

        if (!eventoSubstituiDecisao) {
            // Inicia fase de decisão normal
            EstadoSala estadoDecisao = jogoService.montarEstadoSala(codigoSala);
            estadoDecisao.setFase("DECISAO");
            estadoDecisao.setMensagem("Rodada " + sala.getRodadaAtual() +
                    " iniciada! Faça sua escolha.");
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

    static class MensagemLiderancaVotos {
        public String tipo = "LIDERANCA_VOTOS";
        public java.util.Map<String, Integer> votos;
    }

    static class MensagemLiderEleito {
        public String tipo = "LIDER_ELEITO";
        public String lider;
    }

    static class MensagemResultadoEvento {
        public String tipo = "RESULTADO_EVENTO";
        public String nomeEvento;
        public String emoji;
        public String mensagem;
    }
}