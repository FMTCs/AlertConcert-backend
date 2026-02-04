package fcmt.backend.service;

import fcmt.backend.dto.*;
import fcmt.backend.exception.custom.DuplicateUserException;
import fcmt.backend.exception.custom.TokenInvalidException;
import fcmt.backend.repository.SessionTokenRepository;
import fcmt.backend.repository.SignupTokenRepository;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.security.SessionTokenConfig;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import fcmt.backend.entity.User;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.exception.custom.UserNotFoundException;
import fcmt.backend.exception.custom.InvalidPasswordException;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;

	private final JwtTokenProvider jwtTokenProvider;

	private final SessionTokenRepository sessionTokenRepository;

	private final SignupTokenRepository signupTokenRepository;

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
	public ResponseEntity<?> register(RegisterRequestDto request) {

		// 1. username 중복 검사
		validateDuplicateUsername(request.getUsername());

		// 2. [TODO] 비밀번호 처리 -> 해싱을 어디서 하느냐? + 보안성 검사 + 비밀번호 확인
		// 알던 gemini 왈 service 단에서 처리하는게 맞다고 해서 구현은 해두는 느낌으로 그럼 여기에서 비밀번호 확인이랑 보안성도 검사하는거
		// 아닐까?
		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// 3. Redis의 signupToken을 가지고 있을 때, User table에 채울 정보 가져오기
		Map<Object, Object> registerDatas = signupTokenRepository.find(request.getSignupToken());

		// 4. Redis에서 토큰이 없다면 유효하지 않다면 에러 처리 [TODO] : 토큰 invalid 처리 중복 해결 + 에러 메세지 수정
		if (registerDatas == null || registerDatas.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 세션이 만료되었습니다. Spotify 인증을 다시 진행해주세요.");
		}

		// 5. spotifyRefreshToken 및 spotifyUserId 가져오기 [TODO] RefreshToken 암호화하기
		String spotifyRefreshToken = (String) registerDatas.get("spotifyRefreshToken");
		String spotifyUserId = (String) registerDatas.get("spotifyUserId");

		// 6. User entity 생성
		User user = User.builder()
			.username(request.getUsername())
			.password(encodedPassword)
			.spotifyUserId(spotifyUserId)
			.spotifyRefreshTokenEnc(spotifyRefreshToken) // [TODO] RefreshToken 암호화하기
			.valid(true)
			.build();

		// 7. DB에 저장
		userRepository.save(user);
		// [TODO] 처리 메세지 정리
		return ResponseEntity.ok("Register Success!");
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

	public LogoutResponseDto logout(String token) {
		Claims claims = jwtTokenProvider.parseClaims(token);
		sessionTokenRepository.delete(claims.get("uid", Long.class));

		// [TODO] 논의점 : blackList로 accessToken을 제거하는게 좋을까?
		// long expiration = claims.getExpiration().getTime() -
		// System.currentTimeMillis();
		// if (expiration > 0) {
		// // key: token, value: "logout" 등의 형태로 Redis에 저장 -> Filter에 BlackList 검증 구조 추가
		// sessionTokenRepository.saveBlacklist(token, "logout", expiration / 1000);
		// }

		return LogoutResponseDto.success();
	}

	private void validateDuplicateUsername(String username) {
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateUserException();
		}
	}

}