package fcmt.backend.controller;

import fcmt.backend.service.LoginService;
import fcmt.backend.dto.LoginRequestDto;
import fcmt.backend.dto.LoginResponseDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/login")
public class LoginController {

	private final LoginService loginService;

	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/login")
	public LoginResponseDto login(@RequestBody LoginRequestDto request) {
		return loginService.login(request);
	}

}