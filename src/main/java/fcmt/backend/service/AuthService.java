package fcmt.backend.service;

import fcmt.backend.dto.*;
import fcmt.backend.exception.BusinessException;
import fcmt.backend.repository.SessionTokenRepository;
import fcmt.backend.repository.SignupTokenRepository;
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
import fcmt.backend.exception.ErrorCode;

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
		;
		return userRepository.findByUsername(username)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));
	}

	// Login Service 구현
	public LoginResponseDto login(LoginRequestDto request) {
		// 1. DB에서 사용자 정보 가져오기
		User user = getUser(request.getUsername());
		// 2. ID/PW 검증부
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
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

		// 2. 비밀번호 유효성 검사 + 일치 확인
		validatePassword(request.getPassword(), request.getPasswordConfirm());

		// 3. 비밀번호 해싱
		String encodedPassword = passwordEncoder.encode(request.getPassword());

		// 4. Redis의 signupToken을 가지고 있을 때, User table에 채울 정보 가져오기
		Map<Object, Object> registerDatas = signupTokenRepository.find(request.getSignupToken());
		if (registerDatas == null || registerDatas.isEmpty()) {
			throw new BusinessException(ErrorCode.SESSION_EXPIRED);
		}

		String spotifyRefreshToken = (String) registerDatas.get("spotifyRefreshToken");
		String spotifyUserId = (String) registerDatas.get("spotifyUserId");

		// 6. User entity 생성
		User user = User.builder()
			.username(request.getUsername())
			.password(encodedPassword)
			.spotifyUserId(spotifyUserId)
			.spotifyRefreshTokenEnc(spotifyRefreshToken) // converter에 의해서 자동으로 암호화
			.valid(true)
			.build();

		// 7. DB에 저장
		userRepository.save(user);
		return RegisterResponseDto.success();
	}

	public RefreshResponseDto refresh(String token) {
		// 1. Token에서 uid 추출
		Claims claims = jwtTokenProvider.parseClaimsAllowExpired(token);
		Long uid = claims.get("uid", Long.class);

		// 2. Redis에 해당 토큰이 존재하는지 검색 -> 없으면 에러
		String sessionRefreshToken = sessionTokenRepository.find(uid)
			.orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

		// 3. 해당 토큰이 존재한다면 기존 토큰을 제거
		if (!jwtTokenProvider.isValidateToken(sessionRefreshToken)) {
			sessionTokenRepository.delete(uid);
			throw new BusinessException(ErrorCode.TOKEN_INVALID);
		}

		// 4. User table에서 검색 후 JWT 토큰 생성
		User user = userRepository.findById(uid).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
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
			throw new BusinessException(ErrorCode.DUPLICATE_USER);
		}
	}

	private void validatePassword(String password, String passwordConfirm) {
		// 비밀번호 일치 여부 -> 다른 검증은 Dto 에서 처리 후 넘어옴
		if (!password.equals(passwordConfirm)) {
			throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
		}
	}

}