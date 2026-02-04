package fcmt.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

@Getter
@AllArgsConstructor
public class RegisterRequestDto {

	private String username; // 로그인 시 사용 id

	private String password; // 로그인 시 사용 pw

	private String spotifyUserId; // spotify_user_id

	private String signupToken; // spotify 인증 토큰

}
