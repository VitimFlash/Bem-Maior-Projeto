package game.bmm.repository;

import game.bmm.model.Sala;
import game.bmm.model.Votacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VotacaoRepository extends JpaRepository<Votacao, Long> {
    List<Votacao> findBySala(Sala sala);
    Optional<Votacao> findBySalaAndConcluidaFalse(Sala sala);
}