package fcmt.backend.repository;

import fcmt.backend.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	Optional<Concert> findByConcertName(String concertName); // 콘서트 중복 저장 방지를 위해

	@Query(value = "SELECT DISTINCT c.* FROM concerts c " + "JOIN artists a ON a.artist_id = ANY(c.casts) "
			+ "WHERE c.casts && :preferredIds " + // 1. 선호 가수 포함 공연
			"OR EXISTS (" + "    SELECT 1 FROM unnest(a.genres) AS g " + "    WHERE g ILIKE ANY(ARRAY[:categories]))"
			, nativeQuery = true)
	List<Concert> findRecommendedByCategories(@Param("preferredIds") Long[] preferredIds,
			@Param("categories") String[] categories);

}