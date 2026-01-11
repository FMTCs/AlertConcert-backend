package fcmt.backend.security;

import fcmt.backend.entity.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

	@Value("${jwt.secret}")
	private String secret;

	private Key key;

	// application.yml에 설정해 둔 정보를 기반(시크릿 키, 만료 시간)으로 TokenProvider를 생성
	@PostConstruct
	private void init() {
		if (secret == null || secret.length() < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 characters");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String createSessionToken(User user, SessionTokenConfig type) {
		return createToken(user, type);
	}

	private String createToken(User user, SessionTokenConfig type) {
		return Jwts.builder()
			.setSubject(user.getId())
			.claim("uid", user.getUid())
			.claim("type", type.name())
			.setIssuedAt(new Date())
			.setExpiration(new Date(System.currentTimeMillis() + type.getExpireMillis()))
			.signWith(key, SignatureAlgorithm.HS256)
			.compact();
	}

}
