package fcmt.backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class SessionTokenRepository {

	private final StringRedisTemplate redisTemplate;

	private static final String PREFIX = "session_token";

	public void save(long uid, String refreshToken, long ttlSecons) {
		redisTemplate.opsForValue().set(PREFIX + uid, refreshToken, ttlSecons, TimeUnit.SECONDS);
	}

	public String find(String userId) {
		return redisTemplate.opsForValue().get(PREFIX + userId);
	}

	public void delete(String userId) {
		redisTemplate.delete(PREFIX + userId);
	}

}
