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

    private final Map<String, String> liderEleito =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> estadoBomba =
            new java.util.concurrent.ConcurrentHashMap<>();

    // =============================================
    // REST ENDPOINTS
    // =============================================
    @RestController
    @RequestMapping("/jogo")
    class JogoRestController {

        @PostMapping("/iniciar-rodada")
        public ResponseEntity<?> iniciarRodada(@RequestBody Map<String, String> body) {
            try {
                iniciarNovaRodada(body.get("codigo"));
                return ResponseEntity.ok("Rodada iniciada!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.badRequest().body("Interrompido.");
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
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
                        codigoSala, username, tipo, acao, alvo, body);

                if (resultado.containsKey("notificarSala")) {
                    EstadoSala estado = jogoService.montarEstadoSala(codigoSala);
                    estado.setMensagem((String) resultado.get("mensagemSala"));
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, estado);
                }

                if ("BOMBA_RELOGIO".equals(tipo) && "PASSAR".equals(acao)) {
                    return processarPassagemBomba(codigoSala, username, alvo, auth);
                }

                return ResponseEntity.ok(resultado);
            } catch (RuntimeException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        private ResponseEntity<?> processarPassagemBomba(String codigoSala,
                                                         String username, String proximoPortador, Authentication auth) {
            try {
                Map<String, Object> bomba = estadoBomba.get(codigoSala);
                if (bomba == null) return ResponseEntity.badRequest().body("Bomba não encontrada.");

                int ticks = (int) bomba.get("ticks");
                ticks--;
                bomba.put("ticks", ticks);
                bomba.put("portador", proximoPortador);
                estadoBomba.put(codigoSala, bomba);

                System.out.println("=== BOMBA === Portador: " + proximoPortador +
                        " Ticks restantes: " + ticks);

                if (ticks <= 0) {
                    // EXPLODIU — aplica penalidade no portador atual (quem passou)
                    Sala sala = salaRepository.findByCodigo(codigoSala)
                            .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
                    Jogador portadorAtual = jogadorRepository.findBySalaAndAtivoTrue(sala)
                            .stream()
                            .filter(j -> j.getUsuario().getUsername().equals(username))
                            .findFirst().orElse(null);

                    if (portadorAtual != null) {
                        portadorAtual.setBemPessoal(portadorAtual.getBemPessoal() - 4);
                        jogadorRepository.save(portadorAtual);
                    }

                    // Notifica explosão para todos
                    MensagemResultadoEvento msgExplosao = new MensagemResultadoEvento();
                    msgExplosao.nomeEvento = "BOMBA_RELOGIO";
                    msgExplosao.emoji = "💥";
                    msgExplosao.mensagem = "💥 BOOM! " + username +
                            " estava com a bomba e perdeu 4 moedas!";
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgExplosao);

                    estadoBomba.remove(codigoSala);

                    // Agora sim libera a decisão para todos
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                            EstadoSala estadoDecisao = jogoService.montarEstadoSala(codigoSala);
                            estadoDecisao.setFase("DECISAO");
                            estadoDecisao.setMensagem("Faça sua escolha!");
                            mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoDecisao);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();

                    return ResponseEntity.ok(Map.of("explodiu", true));
                }

                // Passa bomba para próximo portador
                mensageiro.convertAndSendToUser(proximoPortador,
                        "/queue/estado-jogador-evento",
                        Map.of(
                                "fase", "DECISAO_EVENTO_ANTES",
                                "eventoAtualInfo", Map.of(
                                        "tipo", "BOMBA_RELOGIO",
                                        "souPortador", true,
                                        "requerDecisao", true,
                                        "descricao", "💣 Você recebeu a bomba! Passe-a rapidamente!"
                                )
                        )
                );

                // Notifica todos que a bomba foi passada (sem revelar para quem)
                EstadoSala estadoAtualizado = jogoService.montarEstadoSala(codigoSala);
                estadoAtualizado.setMensagem("💣 A bomba foi passada...");
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoAtualizado);

                return ResponseEntity.ok(Map.of("explodiu", false));
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
                        codigo, auth.getName());
                return ResponseEntity.ok(Map.of("sala", estadoSala, "jogador", estadoJogador));
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
                return ResponseEntity.ok(rodadaRepository.findBySalaOrderByNumeroAsc(sala));
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

                // Busca rodada ativa
                List<Rodada> rodadasAtivas = rodadaRepository
                        .findBySalaAndStatusOrderByNumeroDesc(sala, "AGUARDANDO_DECISOES");
                if (rodadasAtivas.isEmpty()) {
                    throw new RuntimeException("Rodada nao encontrada.");
                }
                Rodada rodada = rodadasAtivas.get(0);

                // Cria decisões para todos baseadas na distribuição do líder
                for (Jogador jogador : ativos) {
                    String username = jogador.getUsuario().getUsername();
                    if (distribuicao != null && distribuicao.containsKey(username)) {
                        Map<String, Integer> valores = distribuicao.get(username);
                        int tributo = valores.getOrDefault("tributo", 0);
                        int bem = valores.getOrDefault("bemPessoal", 0);

                        Decisao decisao = new Decisao();
                        decisao.setJogador(jogador);
                        decisao.setRodada(rodada);
                        decisao.setMoedasTributo(tributo);
                        decisao.setMoedasBemPessoal(bem);
                        decisaoRepository.save(decisao);

                        jogador.setBemPessoal(jogador.getBemPessoal() + bem);
                        jogadorRepository.save(jogador);
                    }
                }

                // Remove líder e notifica todos
                liderEleito.remove(codigoSala);

                EstadoSala estado = jogoService.montarEstadoSala(codigoSala);
                estado.setMensagem("👑 O Líder distribuiu as moedas desta rodada!");
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estado);

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

    // =============================================
    // WEBSOCKET — RECEBER DECISÃO
    // =============================================
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
            System.out.println("Decisao registrada!");

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
            e.printStackTrace();
        }
    }

    // =============================================
    // PROCESSAR RODADA
    // =============================================
    private void processarRodada(String codigoSala) {
        new Thread(() -> {
            try {
                System.out.println("=== THREAD processarRodada INICIADA ===");

                // Verifica Bolha/Fogueira ANTES de processar tributos
                Sala salaEv = salaRepository.findByCodigo(codigoSala)
                        .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
                List<Rodada> rodadasAtivas = rodadaRepository
                        .findBySalaAndStatusOrderByNumeroDesc(salaEv, "AGUARDANDO_DECISOES");
                Rodada rodadaAtualEv = rodadasAtivas.isEmpty() ? null : rodadasAtivas.get(0);

                if (rodadaAtualEv != null && rodadaAtualEv.getEvento() != null) {
                    Evento ev = rodadaAtualEv.getEvento();
                    List<Jogador> ativosEv = jogadorRepository.findBySalaAndAtivoTrue(salaEv);

                    if ("BOLHA".equals(ev.getTipo()) || "FOGUEIRA".equals(ev.getTipo())) {
                        List<Decisao> decisoesEv = decisaoRepository.findByRodada(rodadaAtualEv);
                        int totalBemPessoal = decisoesEv.stream()
                                .mapToInt(Decisao::getMoedasBemPessoal).sum();

                        Jogador sorteado = ativosEv.get(
                                new java.util.Random().nextInt(ativosEv.size()));

                        MensagemResultadoEvento msgEv = new MensagemResultadoEvento();

                        if ("BOLHA".equals(ev.getTipo())) {
                            sorteado.setBemPessoal(sorteado.getBemPessoal() + totalBemPessoal);
                            jogadorRepository.save(sorteado);
                            msgEv.nomeEvento = "BOLHA";
                            msgEv.emoji = "🫧";
                            msgEv.mensagem = "🫧 A bolha estourou! " +
                                    sorteado.getUsuario().getUsername() +
                                    " recebeu " + totalBemPessoal + " moedas no bem-pessoal!";
                        } else {
                            sorteado.setBemPessoal(sorteado.getBemPessoal() - totalBemPessoal);
                            jogadorRepository.save(sorteado);
                            msgEv.nomeEvento = "FOGUEIRA";
                            msgEv.emoji = "🔥";
                            msgEv.mensagem = "🔥 A fogueira apagou! " +
                                    sorteado.getUsuario().getUsername() +
                                    " perdeu " + totalBemPessoal + " moedas do bem-pessoal!";
                        }

                        mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgEv);
                        Thread.sleep(5000);
                    }
                }

                String mensagemVeneno = null;
                if (rodadaAtualEv != null && rodadaAtualEv.getEvento() != null &&
                        "VENENO".equals(rodadaAtualEv.getEvento().getTipo())) {

                    String alvoVenenoId = rodadaAtualEv.getEvento().getJogadorAlvoId();
                    // alvoVenenoId aqui seria o ID da vítima escolhida
                    // Verificar se a vítima colocou todas as moedas nos tributos
                    if (alvoVenenoId != null) {
                        List<Decisao> decisoesEv = decisaoRepository.findByRodada(rodadaAtualEv);
                        boolean venenoAtivado = decisoesEv.stream()
                                .filter(d -> d.getJogador().getId().toString().equals(alvoVenenoId))
                                .anyMatch(d -> d.getMoedasTributo() < 5);

                        if (venenoAtivado) {
                            // Aplica penalidade
                            jogadorRepository.findById(Long.parseLong(alvoVenenoId))
                                    .ifPresent(vitima -> {
                                        vitima.setBemPessoal(vitima.getBemPessoal() - 5);
                                        jogadorRepository.save(vitima);
                                    });
                            mensagemVeneno = "ativado";
                        } else {
                            mensagemVeneno = "naoAtivado";
                        }
                    }
                }

                // Envia resultado do veneno antes da revelação
                if (mensagemVeneno != null) {
                    MensagemResultadoEvento msgVeneno = new MensagemResultadoEvento();
                    msgVeneno.nomeEvento = "VENENO";
                    msgVeneno.emoji = "🧪";
                    msgVeneno.mensagem = "ativado".equals(mensagemVeneno)
                            ? "🧪 O veneno foi ativado! A vítima perdeu 5 moedas do bem-pessoal!"
                            : "🧪 O veneno não foi ativado! A vítima colocou todas as moedas nos tributos.";
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgVeneno);
                    Thread.sleep(5000);
                }

                // Processa tributos
                Tributo tributo = jogoService.processarTributos(codigoSala);
                System.out.println("Tributos OK: " + tributo.getTotalArrecadado());

                EstadoSala estadoRevelacao = jogoService.montarEstadoSala(codigoSala);
                estadoRevelacao.setFase("REVELACAO");
                estadoRevelacao.setMensagem(
                        "🏛️ Tributos: " + tributo.getTotalArrecadado() +
                                " moedas! Cada jogador recebeu " +
                                tributo.getPorJogador() + " 🪙. " +
                                tributo.getDescartado() + " descartadas.");
                mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoRevelacao);

                Thread.sleep(5000);

                boolean continua = jogoService.avancarRodada(codigoSala);
                System.out.println("Continua: " + continua);

                if (!continua) {
                    EstadoSala estadoFinal = jogoService.montarEstadoFinal(codigoSala);
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoFinal);
                    return;
                }

                iniciarFaseDiscussao(codigoSala);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrompida: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("=== ERRO EM processarRodada ===");
                e.printStackTrace();
            }
        }).start();
    }

    // =============================================
    // INICIAR NOVA RODADA
    // =============================================
    public void iniciarNovaRodada(String codigoSala) throws InterruptedException {
        try {
            iniciarFaseDiscussao(codigoSala);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            System.err.println("Erro ao iniciar rodada: " + e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    // =============================================
    // FASE DE DISCUSSÃO
    // =============================================
    private void iniciarFaseDiscussao(String codigoSala) throws InterruptedException {
        Sala sala = salaRepository.findByCodigo(codigoSala)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));

        boolean podeVotar = sala.getRodadaAtual() > sala.getTotalRodadas() / 2;

        Thread.sleep(1000);

        EstadoSala estadoDiscussao = jogoService.montarEstadoSala(codigoSala);
        estadoDiscussao.setFase("DISCUSSAO");
        estadoDiscussao.setTempoDiscussao(30);
        estadoDiscussao.setPodeVotar(podeVotar);
        estadoDiscussao.setMensagem(podeVotar
                ? "💬 Tempo de discussão! Você pode votar para eliminar alguém."
                : "💬 Tempo de discussão! A votação estará disponível após a metade do jogo.");
        mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoDiscussao);

        Thread.sleep(30000);

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

        iniciarEventoERodada(codigoSala);
    }

    // =============================================
    // INICIAR EVENTO E RODADA
    // =============================================
    private void iniciarEventoERodada(String codigoSala) throws InterruptedException {
        Sala sala = salaRepository.findByCodigo(codigoSala)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        System.out.println("=== SORTEANDO EVENTO PARA RODADA " + sala.getRodadaAtual() + " ===");

        // Cria rodada
        jogoService.iniciarRodadaERetornarEstado(codigoSala);

        // Busca rodada ativa recém-criada
        List<Rodada> rodadasAtivasList = rodadaRepository
                .findBySalaAndStatusOrderByNumeroDesc(sala, "AGUARDANDO_DECISOES");
        Rodada rodadaAtiva = rodadasAtivasList.isEmpty() ? null : rodadasAtivasList.get(0);

        Evento eventoSorteado = null;

        if (rodadaAtiva != null && eventoService.deveHaverEvento(sala.getRodadaAtual())) {
            eventoSorteado = eventoService.sortearEvento(rodadaAtiva, ativos);

            if (eventoSorteado != null) {
                rodadaAtiva.setEvento(eventoSorteado);
                rodadaRepository.save(rodadaAtiva);

                String descricao = eventoService.gerarDescricao(eventoSorteado, ativos);
                eventoSorteado.setDescricao(descricao);

                final Evento eventoFinal = eventoSorteado;

                // Envia estado personalizado para cada jogador
                for (Jogador jogador : ativos) {
                    String username = jogador.getUsuario().getUsername();
                    EstadoSala estadoPersonalizado = jogoService.montarEstadoSala(codigoSala);
                    estadoPersonalizado.setFase("EVENTO");
                    estadoPersonalizado.setMensagem("⚡ " + eventoFinal.getTipo());

                    EstadoSala.EventoInfo info = jogoService
                            .montarInfoEventoParaJogador(eventoFinal, username, sala);
                    estadoPersonalizado.setEventoAtualInfo(info);

                    mensageiro.convertAndSendToUser(username,
                            "/queue/estado-jogador-evento", estadoPersonalizado);
                }

                // Aguarda 8 segundos de animação
                Thread.sleep(8000);

                // Tratamento especial para LIDERANÇA
                if ("LIDERANCA".equals(eventoFinal.getTipo())) {
                    final String nomeLider = ativos.stream()
                            .filter(j -> j.getId().toString()
                                    .equals(eventoFinal.getJogadorAlvoId()))
                            .map(j -> j.getUsuario().getUsername())
                            .findFirst()
                            .orElse(ativos.get(0).getUsuario().getUsername());

                    liderEleito.put(codigoSala, nomeLider);

                    MensagemLiderEleito msgLider = new MensagemLiderEleito();
                    msgLider.lider = nomeLider;
                    mensageiro.convertAndSend("/topic/sala/" + codigoSala, msgLider);

                    Thread.sleep(3000);

                    EstadoSala estadoLider = jogoService.montarEstadoSala(codigoSala);
                    estadoLider.setFase("LIDERANCA_DISTRIBUIR");
                    estadoLider.setMensagem("👑 Você é o Líder! Distribua as moedas.");
                    mensageiro.convertAndSendToUser(nomeLider,
                            "/queue/estado-jogador-evento", estadoLider);

                    int tentativas = 0;
                    while (liderEleito.containsKey(codigoSala) && tentativas < 60) {
                        Thread.sleep(1000);
                        tentativas++;
                    }
                    return;
                }

                if ("BOMBA_RELOGIO".equals(eventoFinal.getTipo())) {
                    Map<String, Object> bombaState = new java.util.HashMap<>();
                    bombaState.put("portador", eventoFinal.getJogadorAlvoId());
                    bombaState.put("ticks", eventoFinal.getValorEfeito());
                    estadoBomba.put(codigoSala, bombaState);
                    System.out.println("=== BOMBA INICIADA === Ticks: " +
                            eventoFinal.getValorEfeito());
                }

                // Se evento decide ANTES das moedas
                if (jogoService.eventoDecideAntesDasMoedas(eventoFinal.getTipo())) {
                    for (Jogador jogador : ativos) {
                        String username = jogador.getUsuario().getUsername();
                        EstadoSala estadoDecisaoEvento =
                                jogoService.montarEstadoSala(codigoSala);
                        estadoDecisaoEvento.setFase("DECISAO_EVENTO_ANTES");
                        estadoDecisaoEvento.setDecisaoEventoAntes(true);

                        EstadoSala.EventoInfo info = jogoService
                                .montarInfoEventoParaJogador(eventoFinal, username, sala);
                        estadoDecisaoEvento.setEventoAtualInfo(info);

                        mensageiro.convertAndSendToUser(username,
                                "/queue/estado-jogador-evento", estadoDecisaoEvento);
                    }
                    // Aguarda 45 segundos para decisão do evento
                    Thread.sleep(45000);
                }
            }
        }

        // Inicia fase de decisão normal das moedas
        EstadoSala estadoDecisao = jogoService.montarEstadoSala(codigoSala);
        estadoDecisao.setFase("DECISAO");
        estadoDecisao.setMensagem("Rodada " + sala.getRodadaAtual() +
                " iniciada! Faça sua escolha.");

        // Evento que decide DEPOIS das moedas (Roleta, Duplicata, Exposição, Traição)
        if (eventoSorteado != null &&
                !jogoService.eventoDecideAntesDasMoedas(eventoSorteado.getTipo()) &&
                !"LIDERANCA".equals(eventoSorteado.getTipo()) &&
                !"BOLHA".equals(eventoSorteado.getTipo()) &&
                !"FOGUEIRA".equals(eventoSorteado.getTipo())) {
            estadoDecisao.setDecisaoEventoDepois(true);
            EstadoSala.EventoInfo info = new EstadoSala.EventoInfo();
            info.tipo = eventoSorteado.getTipo();
            info.requerDecisao = eventoSorteado.isRequerDecisao();
            estadoDecisao.setEventoAtualInfo(info);
        }

        mensageiro.convertAndSend("/topic/sala/" + codigoSala, estadoDecisao);
    }

    // =============================================
    // CLASSES AUXILIARES
    // =============================================
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