package game.bmm.controller;

import game.bmm.model.EstadoSala;
import game.bmm.model.Jogador;
import game.bmm.model.Sala;
import game.bmm.repository.JogadorRepository;
import game.bmm.service.JogoService;
import game.bmm.service.SalaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sala")
public class SalaController {

    @Autowired private SalaService salaService;
    @Autowired private SimpMessagingTemplate mensageiro;
    @Autowired private JogoService jogoService;
    @Autowired private JogadorRepository jogadorRepository;

    @PostMapping("/criar")
    public ResponseEntity<?> criar(@RequestBody Map<String, Integer> body,
                                   Authentication auth) {
        try {
            Sala sala = salaService.criarSala(auth.getName(), body.get("maxJogadores"));
            return ResponseEntity.ok(Map.of(
                    "codigo", sala.getCodigo(),
                    "maxJogadores", sala.getMaxJogadores(),
                    "totalRodadas", sala.getTotalRodadas(),
                    "meta", sala.getMeta()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/entrar")
    public ResponseEntity<?> entrar(@RequestBody Map<String, String> body,
                                    Authentication auth) {
        try {
            Sala sala = salaService.entrarNaSala(auth.getName(), body.get("codigo"));
            List<String> jogadores = salaService.listarJogadores(sala.getCodigo());

            // Notifica TODOS na sala via WebSocket
            MensagemSala msg = new MensagemSala();
            msg.tipo = "JOGADORES_ATUALIZADOS";
            msg.jogadores = jogadores;
            msg.maxJogadores = sala.getMaxJogadores();
            mensageiro.convertAndSend("/topic/sala/" + sala.getCodigo(), msg);

            return ResponseEntity.ok(Map.of(
                    "codigo", sala.getCodigo(),
                    "maxJogadores", sala.getMaxJogadores()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar(@RequestBody Map<String, String> body,
                                     Authentication auth) {
        System.out.println("=== INICIANDO JOGO ===");
        System.out.println("Codigo: " + body.get("codigo"));
        System.out.println("Usuario: " + auth.getName());
        try {
            String codigo = body.get("codigo");
            Sala sala = salaService.iniciarJogo(codigo);

            // Notifica todos que o jogo começou
            MensagemSala msgInicio = new MensagemSala();
            msgInicio.tipo = "JOGO_INICIADO";
            msgInicio.codigoSala = sala.getCodigo();
            mensageiro.convertAndSend("/topic/sala/" + sala.getCodigo(), msgInicio);

            // Inicia primeira rodada imediatamente
            EstadoSala estadoInicial = jogoService.iniciarRodadaERetornarEstado(codigo);
            estadoInicial.setFase("DECISAO");
            estadoInicial.setMensagem("Rodada 1 iniciada! Faça sua escolha.");
            mensageiro.convertAndSend("/topic/sala/" + codigo, estadoInicial);

            return ResponseEntity.ok("Jogo iniciado!");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{codigo}/jogadores")
    public ResponseEntity<?> jogadores(@PathVariable String codigo) {
        try {
            return ResponseEntity.ok(salaService.listarJogadores(codigo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Novo endpoint — retorna dados completos da sala
    @GetMapping("/{codigo}")
    public ResponseEntity<?> buscarSala(@PathVariable String codigo) {
        try {
            Sala sala = salaService.buscarSala(codigo);
            List<String> jogadores = salaService.listarJogadores(codigo);
            return ResponseEntity.ok(Map.of(
                    "codigo", sala.getCodigo(),
                    "maxJogadores", sala.getMaxJogadores(),
                    "jogadores", jogadores,
                    "status", sala.getStatus()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{codigo}/jogadores-detalhes")
    public ResponseEntity<?> jogadoresDetalhes(@PathVariable String codigo) {
        try {
            Sala sala = salaService.buscarSala(codigo);
            List<Jogador> jogadores = jogadorRepository.findBySalaAndAtivoTrue(sala);
            List<Map<String, Object>> resultado = jogadores.stream()
                    .map(j -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", j.getId());
                        m.put("username", j.getUsuario().getUsername());
                        m.put("contaPessoal", j.getContaPessoal());
                        m.put("eliminado", j.isEliminado());
                        return m;
                    })
                    .toList();
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Endpoint de diagnóstico — remover depois
    @GetMapping("/debug/{codigo}")
    public ResponseEntity<?> debug(@PathVariable String codigo) {
        try {
            Sala sala = salaService.buscarSala(codigo);
            List<String> jogadores = salaService.listarJogadores(codigo);
            return ResponseEntity.ok(Map.of(
                    "codigo", sala.getCodigo(),
                    "status", sala.getStatus(),
                    "maxJogadores", sala.getMaxJogadores(),
                    "jogadoresEncontrados", jogadores.size(),
                    "jogadores", jogadores
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    static class MensagemSala {
        public String tipo;
        public String codigoSala;
        public List<String> jogadores;
        public int maxJogadores;
    }
}