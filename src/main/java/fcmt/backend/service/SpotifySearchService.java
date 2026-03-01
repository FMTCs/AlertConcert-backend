package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.dto.SpotifySearchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SpotifySearchService {

	private final SpotifyAppTokenService spotifyAppTokenService;

	private final RestTemplate restTemplate = new RestTemplate();

	public Optional<AiClient.ArtistIdRecord> searchArtist(String artistName) {
		String accessToken = spotifyAppTokenService.getAppAccessToken();

		String url = "https://api.spotify.com/v1/search?q={q}&type=artist&market=KR&limit=1&offset=0";

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);

		HttpEntity<Void> request = new HttpEntity<>(headers);

		ResponseEntity<SpotifySearchResponseDto> res = restTemplate.exchange(url, HttpMethod.GET, request,
				SpotifySearchResponseDto.class, Map.of("q", artistName));

		SpotifySearchResponseDto body = res.getBody();
		if (body == null || body.getArtists() == null || body.getArtists().getItems() == null) {
			return Optional.empty();
		}

		List<SpotifySearchResponseDto.ArtistItem> items = body.getArtists().getItems();
		if (items.isEmpty() || items.get(0) == null) {
			return Optional.empty();
		}

		var top = items.get(0);
		if (top.getName() == null || top.getId() == null) {
			return Optional.empty();
		}

		return Optional.of(new AiClient.ArtistIdRecord(top.getName(), top.getId()));
	}

}
