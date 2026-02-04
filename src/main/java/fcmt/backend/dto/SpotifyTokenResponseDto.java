package fcmt.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SpotifyTokenResponseDto {

	@JsonProperty("access_token")
	private String accessToken;

	@JsonProperty("token_type")
	private String tokenType;

	@JsonProperty("expires_in")
	private int expiresIn;

	@JsonProperty("refresh_token")
	private String refreshToken;

	private String scope;

}
