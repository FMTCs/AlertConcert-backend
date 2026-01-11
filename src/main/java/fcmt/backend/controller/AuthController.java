package fcmt.backend.controller;

import fcmt.backend.service.AuthService;
import fcmt.backend.dto.LoginRequestDto;
import fcmt.backend.dto.LoginResponseDto;
import fcmt.backend.dto.RegisterRequestDto;
import fcmt.backend.dto.RegisterResponseDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public RegisterResponseDto register(@RequestBody RegisterRequestDto request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public LoginResponseDto login(@RequestBody LoginRequestDto request) {
		return authService.login(request);
	}

}