package game.bmm.repository;

import game.bmm.model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    List<Evento> findByTipo(String tipo);
    List<Evento> findByRequerDecisaoTrue();
}
