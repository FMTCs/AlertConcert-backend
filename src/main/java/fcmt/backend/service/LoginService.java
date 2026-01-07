package fcmt.backend.service;

import org.springframework.stereotype.Service;

@Service
public class LoginService {

	private final UserRepository userRepository;

	public LoginService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public LoginResponseDto login(LoginRequestDto request) {
		// ID/PW 검증부

		// JWT 토큰 생성부
	}

}