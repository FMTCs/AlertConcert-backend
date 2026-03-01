package fcmt.backend.service;

import fcmt.backend.dto.SpotifyAppTokenResponseDto;
import fcmt.backend.exception.BusinessException;
import fcmt.backend.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
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

	// synchronized 최적화 -> 전체에 걸면 토큰을 발급 받는 동안 다른 스레드들이 모두 대기 -> 이미 토큰이 있는 경우는 바로
	public String getAppAccessToken() {
		long now = System.currentTimeMillis();

		// 1. 유효한 토큰이 있으면 즉시 반환 (Lock 없이 빠르게)
		// 만료 30초 전에는 새로 발급
		if (cachedToken != null && now < expiresAtMillis - 30_000) {
			return cachedToken;
		}

		// 2. 토큰 갱신 시에만 동기화 블록 진입
		return refreshAppAccessToken(now);
	}

	// 반환하도록 double-checked-locking 패턴이나 단순 조건문으로 최적화하는 것이 성능상 유리
	public synchronized String refreshAppAccessToken(long now) {

		// 재 진입 후에는 다른 스레드가 이미 갱신했는지 재확인
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

		try {
			ResponseEntity<SpotifyAppTokenResponseDto> response = restTemplate.postForEntity(tokenUrl, request,
					SpotifyAppTokenResponseDto.class);

			SpotifyAppTokenResponseDto body = response.getBody();
			if (body == null || body.getAccessToken() == null || body.getAccessToken().isBlank()) {
				throw new BusinessException(ErrorCode.SPOTIFY_INVALID_TOKEN);
			}

			cachedToken = body.getAccessToken();
			expiresAtMillis = now + (body.getExpiresIn() * 1000L);

			return cachedToken;
		}
		catch (HttpClientErrorException e) {
			// 2. 외부 API에서 4xx 에러를 뱉은 경우 (우리쪽 키가 틀렸을 확률 높음)
			throw new BusinessException(ErrorCode.SPOTIFY_APP_AUTH_FAILED);

		}
		catch (Exception e) {
			throw new BusinessException(ErrorCode.SPOTIFY_API_ERROR);
		}
	}

}
