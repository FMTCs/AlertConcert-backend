package fcmt.backend.controller;

import fcmt.backend.dto.*;
import fcmt.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	// [TODO] ResponseDto 관련 통일 및 처리 필요
	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequestDto request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public LoginResponseDto login(@RequestBody LoginRequestDto request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public RefreshResponseDto refresh(@RequestHeader("Authorization") String authHeader) {
		String accessToken = authHeader.substring(7);
		return authService.refresh(accessToken);
	}

	@PostMapping("/logout")
	public LogoutResponseDto logout(@RequestHeader("Authorization") String authHeader) {
		String accessToken = authHeader.substring(7);
		return authService.logout(accessToken);
	}

}