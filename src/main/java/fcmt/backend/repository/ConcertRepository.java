package fcmt.backend.repository;

import fcmt.backend.entity.Concert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

	Optional<Concert> findByConcertName(String concertName); // 콘서트 중복 저장 방지를 위해

}