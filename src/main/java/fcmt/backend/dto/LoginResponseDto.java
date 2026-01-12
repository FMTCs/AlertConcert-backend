package fcmt.backend.dto;

import lombok.Getter;
import lombok.AllArgsConstructor;

// 로그인 응답 - Access token 제공
@Getter
@AllArgsConstructor
public class LoginResponseDto {

	private String accessToken;

}
