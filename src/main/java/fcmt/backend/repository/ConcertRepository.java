package fcmt.backend.repository;

import fcmt.backend.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	Optional<Concert> findByConcertName(String concertName); // 콘서트 중복 저장 방지를 위해
	@Query(value = "SELECT DISTINCT c.* FROM concerts c WHERE EXISTS (SELECT 1 FROM unnest(c.genres) g WHERE g = ANY(CAST(:preferredGenres AS text[])))",
			nativeQuery = true)
	List<Concert> findByGenresIn(@Param("preferredGenres") String[] preferredGenres);

}