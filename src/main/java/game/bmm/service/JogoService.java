package game.bmm.service;

import game.bmm.model.*;
import game.bmm.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class JogoService {

    @Autowired private SalaRepository salaRepository;
    @Autowired private JogadorRepository jogadorRepository;
    @Autowired private RodadaRepository rodadaRepository;
    @Autowired private DecisaoRepository decisaoRepository;
    @Autowired private TributoRepository tributoRepository;

    // Inicia a primeira rodada
    public Rodada iniciarRodada(String codigoSala) {
        Sala sala = buscarSala(codigoSala);

        Rodada rodada = new Rodada();
        rodada.setSala(sala);
        rodada.setNumero(sala.getRodadaAtual());
        rodada.setStatus("AGUARDANDO_DECISOES");
        rodada.setEvento(null);
        return rodadaRepository.save(rodada);
    }

    // Registra a decisão de um jogador
    public void registrarDecisao(String codigoSala, String username,
                                 int moedasTributo, int moedasBemPessoal) {

        Sala sala = buscarSala(codigoSala);
        Jogador jogador = buscarJogador(username, sala);
        Rodada rodada = buscarRodadaAtiva(sala);

        // Bloqueia jogador eliminado
        if (!jogador.isAtivo() || jogador.isEliminado()) {
            throw new RuntimeException("Jogador eliminado não pode participar.");
        }

        // Valida se já decidiu
        if (decisaoRepository.existsByJogadorAndRodada(jogador, rodada)) {
            throw new RuntimeException("Voce já registrou sua decisão nesta rodada.");
        }

        // Valida se a soma é 5 (ou o valor do evento se alterar)
        int totalMoedas = moedasTributo + moedasBemPessoal;
        if (totalMoedas != 5) {
            throw new RuntimeException("A soma das moedas deve ser 5.");
        }

        // Valida valores negativos
        if (moedasTributo < 0 || moedasBemPessoal < 0) {
            throw new RuntimeException("Valores inválidos.");
        }

        // Salva a decisão
        Decisao decisao = new Decisao();
        decisao.setJogador(jogador);
        decisao.setRodada(rodada);
        decisao.setMoedasTributo(moedasTributo);
        decisao.setMoedasBemPessoal(moedasBemPessoal);
        decisaoRepository.save(decisao);

        // Atualiza bem-pessoal imediatamente
        jogador.setBemPessoal(jogador.getBemPessoal() + moedasBemPessoal);
        jogadorRepository.save(jogador);
    }

    // Verifica se todos os jogadores ativos já decidiram
    public boolean todosDecidiram(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        Rodada rodada = buscarRodadaAtiva(sala);
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);
        List<Decisao> decisoes = decisaoRepository.findByRodada(rodada);

        System.out.println("=== VERIFICANDO DECISOES ===");
        System.out.println("Jogadores ativos: " + ativos.size());
        System.out.println("Decisoes registradas: " + decisoes.size());
        ativos.forEach(j -> System.out.println("  Ativo: " + j.getUsuario().getUsername()));
        decisoes.forEach(d -> System.out.println("  Decidiu: " +
                d.getJogador().getUsuario().getUsername()));

        // Verifica se cada jogador ativo tem uma decisão nesta rodada
        for (Jogador jogador : ativos) {
            boolean temDecisao = decisoes.stream()
                    .anyMatch(d -> d.getJogador().getId().equals(jogador.getId()));
            if (!temDecisao) {
                System.out.println("  FALTANDO: " + jogador.getUsuario().getUsername());
                return false;
            }
        }

        System.out.println("  TODOS DECIDIRAM!");
        return true;
    }

    // Processa os tributos e distribui para conta-pessoal
    public Tributo processarTributos(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        Rodada rodada = buscarRodadaAtiva(sala);

        // Apenas jogadores ATIVOS (não eliminados)
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);
        List<Decisao> decisoes = decisaoRepository.findByRodada(rodada);

        int totalArrecadado = decisoes.stream()
                .mapToInt(Decisao::getMoedasTributo)
                .sum();

        int totalDistribuido = totalArrecadado * 2;
        // Divide apenas pelo número de jogadores ATIVOS
        int porJogador = ativos.isEmpty() ? 0 : totalDistribuido / ativos.size();
        int descartado = ativos.isEmpty() ? totalDistribuido :
                totalDistribuido % ativos.size();

        // Distribui APENAS para jogadores ativos
        for (Jogador jogador : ativos) {
            jogador.setContaPessoal(jogador.getContaPessoal() + porJogador);
            jogadorRepository.save(jogador);
        }

        Tributo tributo = new Tributo();
        tributo.setRodada(rodada);
        tributo.setTotalArrecadado(totalArrecadado);
        tributo.setTotalDistribuido(totalDistribuido);
        tributo.setPorJogador(porJogador);
        tributo.setDescartado(descartado);
        tributoRepository.save(tributo);

        rodada.setTotalTributos(totalArrecadado);
        rodada.setValorDistribuido(porJogador);
        rodada.setMoedasDescartadas(descartado);
        rodada.setStatus("CONCLUIDA");
        rodadaRepository.save(rodada);

        return tributo;
    }

    // Avança para a próxima rodada ou finaliza o jogo
    public boolean avancarRodada(String codigoSala) {
        Sala sala = buscarSala(codigoSala);

        if (sala.getRodadaAtual() >= sala.getTotalRodadas()) {
            // Jogo finalizado
            sala.setStatus("FINALIZADA");
            salaRepository.save(sala);
            return false;
        }

        // Avança rodada
        sala.setRodadaAtual(sala.getRodadaAtual() + 1);
        salaRepository.save(sala);
        return true;
    }

    // Monta o estado público da sala (enviado para todos)
    public EstadoSala montarEstadoSala(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        EstadoSala estado = new EstadoSala();
        estado.setCodigoSala(codigoSala);
        estado.setRodadaAtual(sala.getRodadaAtual());
        estado.setTotalRodadas(sala.getTotalRodadas());
        estado.setMeta(sala.getMeta());

        // Define fase baseado no status da sala
        String fase = sala.getStatus().equals("EM_JOGO") ? "DECISAO" : sala.getStatus();
        estado.setFase(fase);

        boolean podeVotar = sala.getRodadaAtual() > sala.getTotalRodadas() / 2;
        estado.setPodeVotar(podeVotar);

        Map<String, Integer> contas = new LinkedHashMap<>();
        for (Jogador j : ativos) {
            contas.put(j.getUsuario().getUsername(), j.getContaPessoal());
        }
        estado.setContasPessoais(contas);

        try {
            Rodada rodada = buscarRodadaAtiva(sala);
            List<Decisao> decisoes = decisaoRepository.findByRodada(rodada);
            List<String> jaDecidiram = decisoes.stream()
                    .map(d -> d.getJogador().getUsuario().getUsername())
                    .toList();
            estado.setJaDecidiram(jaDecidiram);
            estado.setTotalTributosRodada(rodada.getTotalTributos());
            estado.setValorDistribuido(rodada.getValorDistribuido());
        } catch (Exception e) {
            estado.setJaDecidiram(new ArrayList<>());
        }

        // Inclui info do evento atual se houver
        try {
            Rodada rodada = buscarRodadaAtiva(sala);
            if (rodada.getEvento() != null) {
                Evento ev = rodada.getEvento();
                EstadoSala.EventoInfo info = new EstadoSala.EventoInfo();
                info.tipo = ev.getTipo();
                info.descricao = ev.getDescricao();
                info.requerDecisao = ev.isRequerDecisao();
                info.jogadorAlvoId = ev.getJogadorAlvoId();

                // Define papéis para cada jogador baseado no evento
                // (preenchido individualmente no montarEstadoJogador)
                estado.setEventoAtualInfo(info);
            }
        } catch (Exception ignored) {}

        // Lista todos os jogadores incluindo eliminados para manter na mesa
        List<Jogador> todos = jogadorRepository.findBySala(sala);
        List<String> eliminados = todos.stream()
                .filter(j -> j.isEliminado())
                .map(j -> j.getUsuario().getUsername())
                .toList();
        estado.setJogadoresEliminados(eliminados);

        // Contas pessoais de TODOS (incluindo eliminados para exibir na mesa)
        for (Jogador j : todos) {
            contas.put(j.getUsuario().getUsername(), j.getContaPessoal());
        }
        estado.setContasPessoais(contas);

        return estado;
    }

    // Monta o estado privado do jogador (enviado só para ele)
    public EstadoJogador montarEstadoJogador(String codigoSala, String username) {
        Sala sala = buscarSala(codigoSala);
        Jogador jogador = buscarJogador(username, sala);

        EstadoJogador estado = new EstadoJogador();
        estado.setUsername(username);
        estado.setContaPessoal(jogador.getContaPessoal());
        estado.setBemPessoal(jogador.getBemPessoal());
        estado.setEliminado(jogador.isEliminado());
        estado.setFase(sala.getStatus());
        estado.setMoedasDaRodada(5); // padrão, eventos podem alterar

        return estado;
    }

    // Monta o estado final revelando tudo
    public EstadoSala montarEstadoFinal(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        List<Jogador> todos = jogadorRepository.findBySala(sala);

        EstadoSala estado = montarEstadoSala(codigoSala);
        estado.setFase("FIM");

        Map<String, Integer> bens = new LinkedHashMap<>();
        Map<String, Integer> totais = new LinkedHashMap<>();
        String vencedor = null;
        int maiorTotal = -1;

        for (Jogador j : todos) {
            String nome = j.getUsuario().getUsername();
            int total = j.getContaPessoal() + j.getBemPessoal();
            bens.put(nome, j.getBemPessoal());
            totais.put(nome, total);

            // Vencedor: maior total acima da meta
            if (total >= sala.getMeta() && total > maiorTotal) {
                maiorTotal = total;
                vencedor = nome;
            }
        }

        estado.setBensPessoais(bens);
        estado.setTotaisFinais(totais);
        estado.setVencedor(vencedor);
        estado.setMensagem(vencedor != null
                ? vencedor + " venceu o jogo!"
                : "Nenhum jogador bateu a meta!");

        return estado;
    }

    // Helpers
    private Sala buscarSala(String codigo) {
        return salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));
    }

    private Jogador buscarJogador(String username, Sala sala) {
        return jogadorRepository.findBySala(sala).stream()
                .filter(j -> j.getUsuario().getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Jogador não encontrado."));
    }

    private Rodada buscarRodadaAtiva(Sala sala) {
        List<Rodada> rodadas = rodadaRepository
                .findBySalaAndStatusOrderByNumeroDesc(sala, "AGUARDANDO_DECISOES");
        if (rodadas.isEmpty()) {
            throw new RuntimeException("Nenhuma rodada ativa.");
        }
        return rodadas.get(0); // pega a mais recente
    }

    public EstadoSala iniciarRodadaERetornarEstado(String codigoSala) {
        Sala sala = buscarSala(codigoSala);

        // Verifica se já existe rodada ativa
        try {
            buscarRodadaAtiva(sala);
            // Se chegou aqui, já existe rodada ativa
        } catch (RuntimeException e) {
            // Cria nova rodada
            Rodada rodada = new Rodada();
            rodada.setSala(sala);
            rodada.setNumero(sala.getRodadaAtual());
            rodada.setStatus("AGUARDANDO_DECISOES");
            rodada.setEvento(null);
            rodadaRepository.save(rodada);
        }

        return montarEstadoSala(codigoSala);
    }

    public Map<String, Object> processarAcaoEvento(String codigoSala,
                                                   String username, String tipo, String acao,
                                                   String alvoUsername, Map<String, Object> dadosExtras) {
    Sala sala = buscarSala(codigoSala);
        Jogador executor = buscarJogador(username, sala);
        Jogador alvo = alvoUsername != null && !alvoUsername.isEmpty()
                ? buscarJogador(alvoUsername, sala)
                : null;
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        Map<String, Object> resultado = new HashMap<>();

        switch (tipo) {
            case "ROUBO" -> {
                if ("ROUBAR".equals(acao) && alvo != null) {
                    alvo.setBemPessoal(alvo.getBemPessoal() - 3);
                    executor.setBemPessoal(executor.getBemPessoal() + 3);
                    jogadorRepository.save(alvo);
                    jogadorRepository.save(executor);
                    resultado.put("notificarSala", true);
                    resultado.put("mensagemSala", "💰 " + username + " roubou nesta rodada!");
                }
            }
            case "VENENO" -> {
                if ("ENVENENAR".equals(acao) && alvo != null) {
                    // Armazena alvo do veneno para verificar após decisão
                    resultado.put("alvoVeneno", alvo.getId());
                    resultado.put("mensagem", "Veneno preparado!");
                }
            }
            case "PARCEIROS" -> {
                resultado.put("escolha", acao);
                resultado.put("mensagem", "Escolha registrada!");
            }
            case "LIDERANCA" -> {
                if ("VOTAR".equals(acao) && alvoUsername != null) {
                    // Armazena voto — processado pelo controller
                    resultado.put("voto", alvoUsername);
                    resultado.put("notificarSala", true);
                    resultado.put("mensagemSala", username + " votou!");
                } else if ("DISTRIBUIR".equals(acao)) {
                    // Líder distribui moedas para cada jogador
                    // body contém distribuicao: {username: {tributo: X, bemPessoal: Y}}
                    resultado.put("distribuicaoAplicada", true);
                }
            }
            case "ROLETA" -> {
                if ("GIRAR".equals(acao)) {
                    int resultadoDado = dadosExtras != null
                            ? (int) dadosExtras.getOrDefault("resultado", 0) : 0;
                    boolean ganhou = resultadoDado == 1 || resultadoDado == 8;
                    if (ganhou) {
                        int bemAtual = executor.getBemPessoal();
                        executor.setBemPessoal((int)(bemAtual * 1.5));
                    } else {
                        executor.setBemPessoal(executor.getBemPessoal() - 5);
                    }
                    jogadorRepository.save(executor);
                    resultado.put("ganhou", ganhou);
                    resultado.put("resultadoDado", resultadoDado);
                }
            }
            case "DUPLICATA" -> {
                if ("BEM_PESSOAL".equals(acao)) {
                    boolean dobrou = new java.util.Random().nextBoolean();
                    int bemAtual = executor.getBemPessoal();
                    int novoValor = dobrou ? bemAtual * 2 : bemAtual / 2;
                    executor.setBemPessoal(novoValor);
                    jogadorRepository.save(executor);
                    resultado.put("dobrou", dobrou);
                    resultado.put("bemAntes", bemAtual);
                    resultado.put("bemDepois", novoValor);
                    resultado.put("mensagem", dobrou
                            ? "✅ Sorte! Seu bem-pessoal dobrou: " + bemAtual + " → " + novoValor
                            : "❌ Azar! Seu bem-pessoal foi dividido: " + bemAtual + " → " + novoValor);
                } else if ("TRIBUTO".equals(acao)) {
                    // Será aplicado na fase de decisão — apenas retorna o resultado
                    boolean dobrou = new java.util.Random().nextBoolean();
                    resultado.put("dobrou", dobrou);
                    resultado.put("mensagem", dobrou
                            ? "✅ Sorte! Seus tributos serão dobrados!"
                            : "❌ Azar! Seus tributos serão divididos por 2!");
                } else if ("NENHUM".equals(acao)) {
                    resultado.put("mensagem", "Você optou por não duplicar nada.");
                }
            }
            case "EXPOSICAO" -> {
                if ("ESPIAR".equals(acao) && alvo != null) {
                    resultado.put("bemPessoal", alvo.getBemPessoal());
                }
            }
            case "OSMOSE" -> {
                if ("DESAFIAR".equals(acao) && alvo != null) {
                    int r;
                    if (executor.getBemPessoal() > alvo.getBemPessoal()) {
                        executor.setBemPessoal(executor.getBemPessoal() + 3);
                        alvo.setBemPessoal(alvo.getBemPessoal() - 3);
                        r = 1;
                    } else if (executor.getBemPessoal() < alvo.getBemPessoal()) {
                        executor.setBemPessoal(executor.getBemPessoal() - 3);
                        alvo.setBemPessoal(alvo.getBemPessoal() + 3);
                        r = -1;
                    } else { r = 0; }
                    jogadorRepository.save(executor);
                    jogadorRepository.save(alvo);
                    resultado.put("resultado", r);
                    // Resultado privado — só o desafiante vê
                } else if ("RECUSAR".equals(acao)) {
                    resultado.put("recusou", true);
                }
            }
            case "TRAICAO" -> {
                if ("TRAIR".equals(acao) && alvo != null) {
                    alvo.setBemPessoal(alvo.getBemPessoal() - 3);
                    jogadorRepository.save(alvo);
                    resultado.put("traido", alvo.getId());
                } else if ("ADIVINHAR".equals(acao) && alvo != null) {
                    // Simplificado — em produção comparar com traidor real
                    boolean acertou = new java.util.Random().nextBoolean();
                    if (acertou) {
                        alvo.setBemPessoal(alvo.getBemPessoal() - 3);
                    } else {
                        executor.setBemPessoal(executor.getBemPessoal() - 3);
                    }
                    jogadorRepository.save(alvo);
                    jogadorRepository.save(executor);
                    resultado.put("acertou", acertou);
                }
            }
            case "COPIA" -> {
                if ("COPIAR".equals(acao) && alvo != null) {
                    executor.setBemPessoal(alvo.getBemPessoal());
                    jogadorRepository.save(executor);
                    resultado.put("copiado", alvo.getBemPessoal());
                }
            }
            case "IGUALDADE" -> {
                resultado.put("voto", acao);
            }
            case "BOMBA_RELOGIO" -> {
                if ("PASSAR".equals(acao) && alvo != null) {
                    boolean explodiu = new java.util.Random().nextInt(5) == 0;
                    if (explodiu) {
                        executor.setBemPessoal(executor.getBemPessoal() - 4);
                        jogadorRepository.save(executor);
                        resultado.put("explodiu", true);
                        resultado.put("notificarSala", true);
                        resultado.put("mensagemSala", "💣 BOOM! " + username + " perdeu 4 moedas!");
                    } else {
                        resultado.put("explodiu", false);
                        resultado.put("proximoPortador", alvoUsername);
                    }
                }
            }
        }

        return resultado;
    }

    public EstadoSala.EventoInfo montarInfoEventoParaJogador(
            Evento evento, String username, Sala sala) {

        EstadoSala.EventoInfo info = new EstadoSala.EventoInfo();
        info.tipo = evento.getTipo();
        info.descricao = evento.getDescricao();
        info.requerDecisao = evento.isRequerDecisao();
        info.jogadorAlvoId = evento.getJogadorAlvoId();

        Jogador jogadorAtual = buscarJogador(username, sala);
        String idAtual = jogadorAtual.getId().toString();
        String alvoId = evento.getJogadorAlvoId();

        System.out.println("=== EVENTO INFO ===");
        System.out.println("Tipo: " + evento.getTipo());
        System.out.println("Username: " + username);
        System.out.println("ID atual: " + idAtual);
        System.out.println("Alvo ID: " + alvoId);

        switch (evento.getTipo()) {
            case "ROUBO" -> {
                info.souExecutor = idAtual.equals(alvoId);
                System.out.println("ROUBO - souLadrao: " + info.souExecutor);
            }
            case "VENENO" -> {
                info.souFeiticeiro = idAtual.equals(alvoId);
                System.out.println("VENENO - souFeiticeiro: " + info.souFeiticeiro);
            }
            case "PARCEIROS" -> {
                if (alvoId != null) {
                    String[] ids = alvoId.split(",");
                    info.souParceiro = java.util.Arrays.stream(ids)
                            .anyMatch(id -> id.trim().equals(idAtual));
                    System.out.println("PARCEIROS - souParceiro: " +
                            info.souParceiro + " ids: " + alvoId);
                }
            }
            case "EXPOSICAO" -> {
                info.souExpositor = idAtual.equals(alvoId);
                System.out.println("EXPOSICAO - souExpositor: " + info.souExpositor);
            }
            case "TRAICAO" -> {
                info.souTraidor = idAtual.equals(alvoId);
                System.out.println("TRAICAO - souTraidor: " + info.souTraidor);
            }
            case "BOMBA_RELOGIO" -> {
                info.souPortador = idAtual.equals(alvoId);
                System.out.println("BOMBA - souPortador: " + info.souPortador);
            }
            case "LIDERANCA" -> {
                info.souExecutor = idAtual.equals(alvoId);
                System.out.println("LIDERANCA - souLider: " + info.souExecutor);
            }
            case "ROLETA", "DUPLICATA", "OSMOSE",
                 "COPIA", "IGUALDADE" -> {
                info.souExecutor = true;
            }
        }

        return info;
    }

    public boolean eventoDecideAntesDasMoedas(String tipo) {
        return switch (tipo) {
            case "VENENO", "LIDERANCA", "IGUALDADE",
                 "BOMBA_RELOGIO", "PARCEIROS", "ROUBO", "OSMOSE", "COPIA"-> true;
            default -> false;
        };
    }

    public boolean eventoDecideDepoisDasMoedas(String tipo) {
        return switch (tipo) {
            case  "ROLETA", "DUPLICATA", "EXPOSICAO", "TRAICAO" -> false;
            default -> false;
        };
    }
}