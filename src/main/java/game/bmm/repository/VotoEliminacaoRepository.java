package game.bmm.repository;

import game.bmm.model.Jogador;
import game.bmm.model.Votacao;
import game.bmm.model.VotoEliminacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VotoEliminacaoRepository extends JpaRepository<VotoEliminacao, Long> {
    List<VotoEliminacao> findByVotacao(Votacao votacao);
    boolean existsByVotacaoAndJogador(Votacao votacao, Jogador jogador);

    Optional<VotoEliminacao> findByVotacaoAndJogador(Votacao votacao, Jogador jogador);
}
