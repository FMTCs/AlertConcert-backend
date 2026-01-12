package fcmt.backend.service;

import fcmt.backend.dto.*;
import fcmt.backend.exception.custom.DuplicateUserException;
import fcmt.backend.exception.custom.TokenInvalidException;
import fcmt.backend.repository.SessionTokenRepository;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.security.SessionTokenConfig;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import fcmt.backend.entity.User;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.exception.custom.UserNotFoundException;
import fcmt.backend.exception.custom.InvalidPasswordException;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	private final JwtTokenProvider jwtTokenProvider;

	private final SessionTokenRepository sessionTokenRepository;

	private final PasswordEncoder passwordEncoder;

	public User getUser(String username) {
		// [TODO] User 미 존재, Password 오류 둘다 동일하게 처리하기 => 보안 이슈 해결
		return userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
	}

	// Login Service 구현
	public LoginResponseDto login(LoginRequestDto request) {
		// 1. DB에서 사용자 정보 가져오기
		User user = getUser(request.getUsername());
		// 2. ID/PW 검증부
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new InvalidPasswordException();
		}
		// JWT 토큰 생성부
		String accessToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.ACCESS);
		String refreshToken = jwtTokenProvider.createSessionToken(user, SessionTokenConfig.REFRESH);
		sessionTokenRepository.save(user.getUid(), refreshToken, SessionTokenConfig.REFRESH.getExpireMillis() / 1000);
		return new LoginResponseDto(accessToken);
	}

	@Transactional // 원자성 보장을 위해서 추가
	public RegisterResponseDto register(RegisterRequestDto request) {

		// 1. username 중복 검사
		validateDuplicateUsername(request.getUsername());

		// 2. [TODO] 비밀번호 처리 -> 해싱을 어디서 하느냐? + 보안성 검사 + 비밀번호 확인
		// 알던 gemini 왈 service 단에서 처리하는게 맞다고 해서 구현은 해두는 느낌으로 그럼 여기에서 비밀번호 확인이랑 보안성도 검사하는거
		// 아닐까?
		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// 3. [TODO] signupToken 가져오기 + spotifyUserId 검증부
		String signupToken = request.getSignupToken();
		if (!signupToken.equals("valid")) {
			log.error("signupToken 오류 - 일단 pass");
			// 임시 response 처리
			return RegisterResponseDto.fail();
		}
		String spotifyUserId = "temp_" + request.getUsername();;

		// 4. User entity 생성
		User user = User.builder()
			.username(request.getUsername())
			.password(encodedPassword)
			.spotifyUserId(spotifyUserId)
			.build();

		// 5. DB에 저장
		userRepository.save(user);
		return RegisterResponseDto.success();
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

	private void validateDuplicateUsername(String username) {
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateUserException();
		}
	}

}