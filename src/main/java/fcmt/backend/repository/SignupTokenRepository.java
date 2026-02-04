package fcmt.backend.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class SignupTokenRepository {

	private final StringRedisTemplate redisTemplate;

	private static final String PREFIX = "signup_token";

	public void save(String signupToken, String spotifyUserId, String spotifyRefreshToken) {
		String key = PREFIX + signupToken;

		// Hash에 여러 필드 저장
		redisTemplate.opsForHash().put(key, "spotifyUserId", spotifyUserId);
		redisTemplate.opsForHash().put(key, "spotifyRefreshToken", spotifyRefreshToken);

		// 전체 키에 대한 만료 시간 설정 (10분)
		redisTemplate.expire(key, 10, TimeUnit.MINUTES);
	}

	public Map<Object, Object> find(String signupToken) {
		return redisTemplate.opsForHash().entries(PREFIX + signupToken);
	}

	public void delete(String signupToken) {
		redisTemplate.delete(PREFIX + signupToken);
	}

}
