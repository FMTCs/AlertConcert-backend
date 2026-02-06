package fcmt.backend.controller;
// TODO: 테스트를 위해 ConcertTestingController를 생성했음. 삭제 필요.

import fcmt.backend.ai.AiClient;
import fcmt.backend.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class ConcertTestingController {

	private final ConcertService concertService;

	private final AiClient aiClient;

	@GetMapping("/run-kopis")
	public String runKopis() {
		try {
			// 4시까지 안 기다리고 바로 수집 로직 실행
			concertService.syncKopisData();
			return "[O] KOPIS 데이터 수집 요청 성공! 서버 로그를 확인하세요.";
		}
		catch (Exception e) {
			e.printStackTrace();
			return "[X] 수집 실패: " + e.getMessage();
		}
	}

	@GetMapping("/ai-test")
	public ResponseEntity<?> testAi(@RequestParam String concertName) {
		// 1) AI로 아티스트 리스트 뽑기 (실패하면 List.of() 반환)
		List<String> artists = aiClient.fetchArtistList(concertName);

		// 2) 아티스트별로 Spotify API 호출 결과 누적
		List<AiClient.ArtistIdRecord> spotifyDetails = new ArrayList<>();

		for (String artistName : artists) {
			if (artistName == null || artistName.isBlank()) {
				continue;
			}

			// ====== Spotify API 호출 (pseudo code) ======
			// AiClient.ArtistIdRecord detail = spotifyClient.searchArtistId(artistName);
			// if (detail != null) spotifyDetails.add(detail);
			// ==========================================
		}

		// 3) 테스트 응답
		return ResponseEntity.ok(Map.of("concertName", concertName, "artistList", artists, // 빈
																							// 리스트면
																							// 그냥
																							// []
																							// 로
																							// 나감
				"spotifyDetails", spotifyDetails, // 못 찾으면 []
				"meta", Map.of("artistCount", artists.size(), "spotifyHitCount", spotifyDetails.size())));
	}

}