package game.bmm.service;

import game.bmm.model.*;
import game.bmm.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VotacaoService {

    @Autowired private VotacaoRepository votacaoRepository;
    @Autowired private VotoEliminacaoRepository votoEliminacaoRepository;
    @Autowired private JogadorRepository jogadorRepository;
    @Autowired private SalaRepository salaRepository;

    // Cada jogador vota individualmente em quem quer eliminar
    public void registrarVotoIndividual(String codigoSala,
                                        String usernameVotante,
                                        String usernameAlvo) {
        Sala sala = buscarSala(codigoSala);
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        Jogador votante = ativos.stream()
                .filter(j -> j.getUsuario().getUsername().equals(usernameVotante))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Votante nao encontrado."));

        // Remove voto anterior se existir
        Votacao votacaoAnterior = buscarOuCriarVotacaoDiscussao(sala);
        votoEliminacaoRepository.findByVotacaoAndJogador(votacaoAnterior, votante)
                .ifPresent(v -> votoEliminacaoRepository.delete(v));

        // Se não pulou, registra novo voto
        if (usernameAlvo != null && !usernameAlvo.equals("PULAR")) {
            Jogador alvo = ativos.stream()
                    .filter(j -> j.getUsuario().getUsername().equals(usernameAlvo))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Alvo nao encontrado."));

            VotoEliminacao voto = new VotoEliminacao();
            voto.setVotacao(votacaoAnterior);
            voto.setJogador(votante);
            voto.setFavor(true);
            // Armazena o alvo no campo jogadorAlvoId da votação
            voto.setAlvoUsername(usernameAlvo);
            votoEliminacaoRepository.save(voto);
        } else {
            // Registra pulo
            VotoEliminacao voto = new VotoEliminacao();
            voto.setVotacao(votacaoAnterior);
            voto.setJogador(votante);
            voto.setFavor(false); // false = pular
            voto.setAlvoUsername("PULAR");
            votoEliminacaoRepository.save(voto);
        }
    }

    // Processa resultado — quem tiver mais votos é eliminado
    // Se empate ou maioria pulou, ninguém é eliminado
    public ResultadoVotacao processarResultado(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        Votacao votacao = buscarOuCriarVotacaoDiscussao(sala);
        List<VotoEliminacao> votos = votoEliminacaoRepository.findByVotacao(votacao);
        List<Jogador> ativos = jogadorRepository.findBySalaAndAtivoTrue(sala);

        // Conta votos por jogador
        Map<String, Integer> contagem = new HashMap<>();
        int totalPulos = 0;

        for (VotoEliminacao v : votos) {
            if ("PULAR".equals(v.getAlvoUsername()) || !v.isFavor()) {
                totalPulos++;
            } else {
                contagem.merge(v.getAlvoUsername(), 1, Integer::sum);
            }
        }

        ResultadoVotacao resultado = new ResultadoVotacao();
        resultado.setContagem(contagem);
        resultado.setTotalPulos(totalPulos);

        if (contagem.isEmpty()) {
            resultado.setEliminado(false);
            resultado.setMensagem("Nenhum jogador foi eliminado.");
            return resultado;
        }

        // Acha o mais votado
        int maxVotos = Collections.max(contagem.values());

        // Verifica se os pulos são maiores ou iguais ao mais votado
        if (totalPulos >= maxVotos) {
            resultado.setEliminado(false);
            resultado.setMensagem("Votação encerrada. Ninguém foi eliminado.");
            return resultado;
        }

        // Verifica empate
        long qtdComMaxVotos = contagem.values().stream()
                .filter(v -> v == maxVotos).count();

        if (qtdComMaxVotos > 1) {
            resultado.setEliminado(false);
            resultado.setMensagem("Empate na votação! Ninguém foi eliminado.");
            return resultado;
        }

        // Elimina o mais votado
        String eliminadoUsername = contagem.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (eliminadoUsername != null) {
            Jogador eliminado = ativos.stream()
                    .filter(j -> j.getUsuario().getUsername().equals(eliminadoUsername))
                    .findFirst().orElse(null);

            if (eliminado != null) {
                eliminado.setEliminado(true);
                eliminado.setAtivo(false);
                jogadorRepository.save(eliminado);
            }

            resultado.setEliminado(true);
            resultado.setJogadorEliminado(eliminadoUsername);
            resultado.setMensagem("🗳️ " + eliminadoUsername +
                    " foi eliminado com " + maxVotos + " votos!");
        }

        // Limpa votação
        votacao.setConcluida(true);
        votacaoRepository.save(votacao);

        return resultado;
    }

    private Votacao buscarOuCriarVotacaoDiscussao(Sala sala) {
        return votacaoRepository.findBySalaAndConcluidaFalse(sala)
                .orElseGet(() -> {
                    Votacao v = new Votacao();
                    v.setSala(sala);
                    v.setConcluida(false);
                    v.setEliminado(false);
                    return votacaoRepository.save(v);
                });
    }

    public Map<String, Integer> buscarContagemVotos(String codigoSala) {
        Sala sala = buscarSala(codigoSala);
        Votacao votacao = buscarOuCriarVotacaoDiscussao(sala);
        List<VotoEliminacao> votos = votoEliminacaoRepository.findByVotacao(votacao);
        Map<String, Integer> contagem = new HashMap<>();
        int pulos = 0;
        for (VotoEliminacao v : votos) {
            if ("PULAR".equals(v.getAlvoUsername()) || !v.isFavor()) pulos++;
            else contagem.merge(v.getAlvoUsername(), 1, Integer::sum);
        }
        contagem.put("__PULOS__", pulos);
        return contagem;
    }

    private Sala buscarSala(String codigo) {
        return salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
    }

    // Classe interna de resultado
    public static class ResultadoVotacao {
        private boolean eliminado;
        private String jogadorEliminado;
        private String mensagem;
        private Map<String, Integer> contagem;
        private int totalPulos;

        public boolean isEliminado() { return eliminado; }
        public void setEliminado(boolean eliminado) { this.eliminado = eliminado; }
        public String getJogadorEliminado() { return jogadorEliminado; }
        public void setJogadorEliminado(String j) { this.jogadorEliminado = j; }
        public String getMensagem() { return mensagem; }
        public void setMensagem(String mensagem) { this.mensagem = mensagem; }
        public Map<String, Integer> getContagem() { return contagem; }
        public void setContagem(Map<String, Integer> c) { this.contagem = c; }
        public int getTotalPulos() { return totalPulos; }
        public void setTotalPulos(int t) { this.totalPulos = t; }
    }
}