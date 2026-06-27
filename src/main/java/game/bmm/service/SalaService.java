package game.bmm.service;

import game.bmm.model.*;
import game.bmm.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SalaService {

    @Autowired private SalaRepository salaRepository;
    @Autowired private JogadorRepository jogadorRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    public Sala criarSala(String usernameDonoSala, int maxJogadores) {
        Sala sala = new Sala();
        sala.setCodigo(gerarCodigo());
        sala.setMaxJogadores(maxJogadores);
        sala.setTotalRodadas(maxJogadores * 2);
        sala.setRodadaAtual(0);
        sala.setMeta((int)(maxJogadores * 5 * 2.4));
        sala.setStatus("AGUARDANDO_JOGADORES");
        salaRepository.save(sala);

        // Dono da sala já entra automaticamente
        entrarNaSala(usernameDonoSala, sala.getCodigo());

        return sala;
    }

    public Sala entrarNaSala(String username, String codigo) {
        Sala sala = salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));

        if (!sala.getStatus().equals("AGUARDANDO_JOGADORES")) {
            throw new RuntimeException("Jogo já iniciado.");
        }

        List<Jogador> jogadores = jogadorRepository.findBySala(sala);
        if (jogadores.size() >= sala.getMaxJogadores()) {
            throw new RuntimeException("Sala cheia.");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        // Verifica se já está na sala
        boolean jaEstaNaSala = jogadores.stream()
                .anyMatch(j -> j.getUsuario().getUsername().equals(username));
        if (jaEstaNaSala) {
            throw new RuntimeException("Você já está nesta sala.");
        }

        Jogador jogador = new Jogador();
        jogador.setUsuario(usuario);
        jogador.setSala(sala);
        jogador.setContaPessoal(0);
        jogador.setBemPessoal(0);
        jogador.setEliminado(false);
        jogador.setAtivo(true);
        jogadorRepository.save(jogador);

        return sala;
    }

    public Sala iniciarJogo(String codigo) {
        Sala sala = salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));

        // Log para debug
        System.out.println("Iniciando jogo sala: " + codigo +
                " status: " + sala.getStatus() +
                " jogadores: " + jogadorRepository.findBySala(sala).size());

        List<Jogador> jogadores = jogadorRepository.findBySala(sala);
        if (jogadores.size() < 2) {
            throw new RuntimeException("Minimo de 2 jogadores para iniciar.");
        }

        // Aceita qualquer status para evitar bloqueio
        sala.setStatus("EM_JOGO");
        sala.setRodadaAtual(1);
        return salaRepository.save(sala);
    }

    public List<String> listarJogadores(String codigo) {
        Sala sala = salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala não encontrada."));
        return jogadorRepository.findBySala(sala)
                .stream()
                .map(j -> j.getUsuario().getUsername())
                .toList();
    }

    // Gera código de 6 caracteres único
    private String gerarCodigo() {
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        String codigo;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
            }
            codigo = sb.toString();
        } while (salaRepository.existsByCodigo(codigo));
        return codigo;
    }

    public Sala buscarSala(String codigo) {
        return salaRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RuntimeException("Sala nao encontrada."));
    }
}
