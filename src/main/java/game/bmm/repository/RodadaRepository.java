package game.bmm.repository;

import game.bmm.model.Rodada;
import game.bmm.model.Sala;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RodadaRepository extends JpaRepository<Rodada, Long> {
    List<Rodada> findBySalaOrderByNumeroAsc(Sala sala);
    Optional<Rodada> findBySalaAndNumero(Sala sala, int numero);
    Optional<Rodada> findBySalaAndStatus(Sala sala, String status);
}
