package fcmt.backend.repository;

import fcmt.backend.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

	Optional<Artist> findByspotifyArtistId(String spotifyArtistId);

}
