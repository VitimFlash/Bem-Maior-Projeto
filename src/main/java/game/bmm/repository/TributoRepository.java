package game.bmm.repository;

import game.bmm.model.Rodada;
import game.bmm.model.Tributo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TributoRepository extends JpaRepository<Tributo, Long> {
    Optional<Tributo> findByRodada(Rodada rodada);
}