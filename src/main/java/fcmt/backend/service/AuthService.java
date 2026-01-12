package fcmt.backend.service;

import fcmt.backend.dto.*;
import fcmt.backend.exception.custom.TokenInvalidException;
import fcmt.backend.repository.SessionTokenRepository;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.security.SessionTokenConfig;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fcmt.backend.entity.User;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.exception.custom.UserNotFoundException;
import fcmt.backend.exception.custom.InvalidPasswordException;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	private final JwtTokenProvider jwtTokenProvider;

	private final SessionTokenRepository sessionTokenRepository;

	public User getUser(String id) {
		return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
	}

	public boolean pwMatch(String pw1, String pw2) {
		return pw1.equals(pw2);
	}

	// Login Service 구현
	public LoginResponseDto login(LoginRequestDto request) {
		// 1. DB에서 사용자 정보 가져오기
		User user = getUser(request.getId());
		// 2. ID/PW 검증부
		if (!pwMatch(request.getPassword(), user.getPassword())) {
			throw new InvalidPasswordException();
		}
		// JWT 토큰 생성부
		String accessToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.ACCESS);
		String refreshToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.REFRESH);
		sessionTokenRepository.save(user.getUid(), refreshToken, SessionTokenConfig.REFRESH.getExpireMillis() / 1000);
		return new LoginResponseDto(accessToken);
	}

	public RegisterResponseDto register(RegisterRequestDto request) {

		return new RegisterResponseDto();
	}

	public RefreshResponseDto refresh(String token) {
		// 1. Token에서 uid 추출
		Claims claims = jwtTokenProvider.parseClaimsAllowExpired(token);
		Long uid = claims.get("uid", Long.class);

		// 2. Redis에 해당 토큰이 존재하는지 검색 -> 없으면 에러
		String sessionRefreshToken = sessionTokenRepository.find(uid).orElseThrow(TokenInvalidException::new);

		// 3. 해당 토큰이 존재한다면 기존 토큰을 제거
		if (!jwtTokenProvider.isValidateToken(sessionRefreshToken)) {
			sessionTokenRepository.delete(uid);
			throw new TokenInvalidException();
		}

		// 4. User table에서 검색 후 JWT 토큰 생성
		User user = userRepository.findById(uid).orElseThrow(UserNotFoundException::new);
		String accessToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.ACCESS);
		String refreshToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.REFRESH);

		// 5. Refresh Token을 새로 등록
		sessionTokenRepository.save(uid, refreshToken, SessionTokenConfig.REFRESH.getExpireMillis() / 1000);

		// 6. accessToken 보내기
		return new RefreshResponseDto(accessToken);
	}

}