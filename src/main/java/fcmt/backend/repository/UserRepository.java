package fcmt.backend.repository;

import java.util.Optional;

import fcmt.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username); // username을 기반으로 User table 탐색

	boolean existsByUsername(String username);

}