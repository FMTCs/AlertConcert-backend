package fcmt.backend.service;

import fcmt.backend.dto.SpotifyAppTokenResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class SpotifyAppTokenService {

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	private final RestTemplate restTemplate = new RestTemplate();

	private volatile String cachedToken;

	private volatile long expiresAtMillis;

	public synchronized String getAppAccessToken() {
		long now = System.currentTimeMillis();

		// 만료 30초 전에는 새로 발급
		if (cachedToken != null && now < expiresAtMillis - 30_000) {
			return cachedToken;
		}

		String tokenUrl = "https://accounts.spotify.com/api/token";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "client_credentials");

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

		ResponseEntity<SpotifyAppTokenResponseDto> response = restTemplate.postForEntity(tokenUrl, request,
				SpotifyAppTokenResponseDto.class);

		SpotifyAppTokenResponseDto body = response.getBody();
		if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
			throw new RuntimeException("Spotify App Token 발급 실패");
		}

		cachedToken = body.getAccessToken();
		expiresAtMillis = now + (body.getExpiresIn() * 1000L);

		return cachedToken;
	}

}
