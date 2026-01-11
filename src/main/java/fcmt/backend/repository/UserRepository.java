package fcmt.backend.repository;

import org.springframework.stereotype.Repository;
import java.util.Optional;

import fcmt.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findById(String id); // id를 기반으로 User table 탐색

}