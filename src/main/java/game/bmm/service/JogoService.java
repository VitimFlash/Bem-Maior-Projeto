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
        return decisoes.size() >= ativos.size();
    }

    // Processa os tributos e distribui para conta-pessoal
    public Tributo processarTributos(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        Rodada rodada = buscarRodadaAtiva(sala);
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);
        List<Decisao> decisoes = decisaoRepository.findByRodada(rodada);

        // Soma todos os tributos
        int totalArrecadado = decisoes.stream()
                .mapToInt(Decisao::getMoedasTributo)
                .sum();

        int totalDistribuido = totalArrecadado * 2;
        int porJogador = totalDistribuido / ativos.size(); // inteiro
        int descartado = totalDistribuido % ativos.size(); // sobra descartada

        // Distribui para a conta-pessoal de cada jogador ativo
        for (Jogador jogador : ativos) {
            jogador.setContaPessoal(jogador.getContaPessoal() + porJogador);
            jogadorRepository.save(jogador);
        }

        // Salva o tributo da rodada
        Tributo tributo = new Tributo();
        tributo.setRodada(rodada);
        tributo.setTotalArrecadado(totalArrecadado);
        tributo.setTotalDistribuido(totalDistribuido);
        tributo.setPorJogador(porJogador);
        tributo.setDescartado(descartado);
        tributoRepository.save(tributo);

        // Conclui a rodada
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
        return rodadaRepository.findBySalaAndStatus(sala, "AGUARDANDO_DECISOES")
                .orElseThrow(() -> new RuntimeException("Nenhuma rodada ativa."));
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
                                                   String username, String tipo, String acao, String alvoUsername) {

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
            case "ROLETA" -> {
                if ("GIRAR".equals(acao)) {
                    int efeito = (int) resultado.getOrDefault("efeito", 0);
                    executor.setBemPessoal(executor.getBemPessoal() + efeito);
                    jogadorRepository.save(executor);
                    resultado.put("aplicado", true);
                }
            }
            case "DUPLICATA" -> {
                if ("BEM_PESSOAL".equals(acao)) {
                    boolean dobrou = new java.util.Random().nextBoolean();
                    int bemAtual = executor.getBemPessoal();
                    executor.setBemPessoal(dobrou ? bemAtual * 2 : bemAtual / 2);
                    jogadorRepository.save(executor);
                    resultado.put("dobrou", dobrou);
                    resultado.put("mensagem", dobrou ? "Dobrou!" : "Dividiu por 2!");
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
}