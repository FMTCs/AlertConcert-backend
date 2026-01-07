package fcmt.backend.repository;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

	public Optional<User> findByUsername(String username) {
        if(username.equals("admin@test.com")) {
            return Optional.of(
                    new User(1L, "admin@test.com", "1234");
            );
        }
        return Optional.empty();
    }

}