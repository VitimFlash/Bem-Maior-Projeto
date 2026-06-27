package game.bmm.controller;

import game.bmm.service.VotacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/votacao")
public class VotacaoController {

    @Autowired private VotacaoService votacaoService;
    @Autowired private SimpMessagingTemplate mensageiro;

    // Jogador vota em alguém ou pula
    @PostMapping("/votar-individual")
    public ResponseEntity<?> votarIndividual(@RequestBody Map<String, String> body,
                                             Authentication auth) {
        try {
            String codigoSala = body.get("codigoSala");
            String alvo = body.get("alvo"); // username ou "PULAR"

            votacaoService.registrarVotoIndividual(
                    codigoSala, auth.getName(), alvo
            );

            // Envia contagem atualizada para todos
            Map<String, Integer> contagem =
                    votacaoService.buscarContagemVotos(codigoSala);

            MensagemVotacao msg = new MensagemVotacao();
            msg.tipo = "VOTACAO_ATUALIZADA";
            msg.contagem = contagem;
            mensageiro.convertAndSend("/topic/sala/" + codigoSala, msg);

            return ResponseEntity.ok("Voto registrado!");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Retorna contagem atual
    @GetMapping("/contagem/{codigoSala}")
    public ResponseEntity<?> contagem(@PathVariable String codigoSala) {
        return ResponseEntity.ok(votacaoService.buscarContagemVotos(codigoSala));
    }

    static class MensagemVotacao {
        public String tipo;
        public Map<String, Integer> contagem;
        public boolean eliminado;
        public String jogadorEliminado;
        public String mensagem;
    }
}