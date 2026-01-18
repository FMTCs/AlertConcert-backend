package fcmt.backend.repository;

import fcmt.backend.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	Optional<Concert> findByConcertName(String concertName); // 콘서트 중복 저장 방지를 위해

	List<Concert> findByUpdatedAtAfter(OffsetDateTime startOfToday);

	@Query(value = "SELECT * FROM concerts c " + "WHERE c.genres && CAST(:preferredGenres AS text[]) "
			+ "OR EXISTS (SELECT 1 FROM jsonb_array_elements(c.casts) as cast_obj "
			+ "WHERE cast_obj->>'id' = ANY(CAST(:artistIds AS text[])))", nativeQuery = true)
	List<Concert> findByGenresIn(@Param("preferredGenres") String[] preferredGenres,
			@Param("artistIds") String[] artistIds);

}