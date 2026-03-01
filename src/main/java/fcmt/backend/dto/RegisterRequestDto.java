package fcmt.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

@Getter
@AllArgsConstructor
public class RegisterRequestDto {

	// 이런 방식으로 Dto에서 Service 자체에 넘어가기 전에 막아버림
	@NotBlank(message = "아이디를 입력하지 않았습니다")
	@Size(min = 4, max = 12, message = "아이디는 4~12자 사이여야 합니다.")
	private String username; // 로그인 시 사용 id

	@NotBlank(message = "비밀번호를 입력하지 않았습니다.")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
			message = "비밀번호는 8자 이상, 20자 이하, 영문, 숫자, 특수문자를 포함해야 합니다.")
	private String password; // 로그인 시 사용 pw

	@NotBlank(message = "비밀번호 확인를 입력하지 않았습니다.")
	private String passwordConfirm; // pw 확인용 -> 일치 확인

	private String spotifyUserId; // spotify_user_id

	private String signupToken; // spotify 인증 토큰 - 정상 회원 가입 확인용

}
