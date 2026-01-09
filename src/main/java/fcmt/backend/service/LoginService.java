package fcmt.backend.service;

import org.springframework.stereotype.Service;

import fcmt.backend.entity.User;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.dto.LoginRequestDto;
import fcmt.backend.dto.LoginResponseDto;
import fcmt.backend.exception.custom.UserNotFoundException;
import fcmt.backend.exception.custom.InvalidPasswordException;

@Service
public class LoginService {

	private final UserRepository userRepository;

	public LoginService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User getUser(String id) {
		return userRepository.findByLongId(id).orElseThrow(UserNotFoundException::new);
	}

	public boolean pwMatch(String pw1, String pw2) {
		return pw1.equals(pw2);
	}

	public LoginResponseDto login(LoginRequestDto request) {
		// 1. DB에서 사용자 정보 가져오기
		User user = getUser(request.getId());
		// 2. ID/PW 검증부
		if (!pwMatch(request.getPassword(), user.getPassword())) {
			throw new InvalidPasswordException();
		}
		// JWT 토큰 생성부
		String token = "";
		return new LoginResponseDto(token);
	}

}