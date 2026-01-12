package fcmt.backend.service;

import fcmt.backend.repository.SessionTokenRepository;
import fcmt.backend.security.JwtTokenProvider;
import fcmt.backend.security.SessionTokenConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import fcmt.backend.entity.User;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.dto.LoginRequestDto;
import fcmt.backend.dto.LoginResponseDto;
import fcmt.backend.dto.RegisterRequestDto;
import fcmt.backend.dto.RegisterResponseDto;
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

}