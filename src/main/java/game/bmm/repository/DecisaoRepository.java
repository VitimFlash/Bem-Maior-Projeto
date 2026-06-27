package game.bmm.repository;

import game.bmm.model.Decisao;
import game.bmm.model.Jogador;
import game.bmm.model.Rodada;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DecisaoRepository extends JpaRepository<Decisao, Long> {
    List<Decisao> findByRodada(Rodada rodada);
    Optional<Decisao> findByJogadorAndRodada(Jogador jogador, Rodada rodada);
    boolean existsByJogadorAndRodada(Jogador jogador, Rodada rodada);
}