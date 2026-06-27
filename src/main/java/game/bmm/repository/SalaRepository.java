package game.bmm.repository;

import game.bmm.model.Sala;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SalaRepository extends JpaRepository<Sala, Long> {
    Optional<Sala> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
}
