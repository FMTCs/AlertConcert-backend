package fcmt.backend.service;

import fcmt.backend.dto.SpotifyTokenResponseDto;
import fcmt.backend.dto.SpotifyUserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class SpotifyOAuthService {

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	@Value("${spotify.api.redirect-uri}")
	private String redirectUri;

	private final RestTemplate restTemplate = new RestTemplate();

	public SpotifyTokenResponseDto getTokens(String code) {
		String tokenUrl = "https://accounts.spotify.com/api/token";

		// 헤더 설정 (Content-Type: application/x-www-form-urlencoded)
		// Client ID와 Secret을 Basic Auth로 인코딩 후 전송
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);

		// 바디 설정
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "authorization_code");
		params.add("code", code);
		params.add("redirect_uri", redirectUri);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

		ResponseEntity<SpotifyTokenResponseDto> response = restTemplate.postForEntity(tokenUrl, request,
				SpotifyTokenResponseDto.class);

		SpotifyTokenResponseDto tokenResponse = response.getBody();

		if (tokenResponse == null || tokenResponse.getAccessToken() == null
				|| tokenResponse.getRefreshToken() == null) {
			throw new RuntimeException("SpotifyAuthorize Error!");
		}

		return tokenResponse;
	}

	public SpotifyUserDto getSpotifyUserInfo(String accessToken) {
		String userInfoUrl = "https://api.spotify.com/v1/me";

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);

		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<SpotifyUserDto> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request,
				SpotifyUserDto.class);

		if (response.getBody() == null) {
			throw new RuntimeException("SpotifyAuthorize Error!");
		}

		return response.getBody();
	}

}
