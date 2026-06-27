package game.bmm.repository;

import game.bmm.model.Jogador;
import game.bmm.model.Sala;
import game.bmm.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JogadorRepository extends JpaRepository<Jogador, Long> {
    List<Jogador> findBySala(Sala sala);
    List<Jogador> findBySalaAndAtivoTrue(Sala sala);
    Optional<Jogador> findByUsuarioAndSala(Usuario usuario, Sala sala);
}