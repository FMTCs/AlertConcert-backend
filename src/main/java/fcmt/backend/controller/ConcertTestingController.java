package fcmt.backend.controller;
// TODO: 테스트를 위해 ConcertTestingController를 생성했음. 삭제 필요.

import fcmt.backend.ai.AiClient;
import fcmt.backend.service.ConcertCastApplyService;
import fcmt.backend.service.ConcertService;
import fcmt.backend.service.SpotifySearchService;
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

	private final ConcertCastApplyService concertCastApplyService;

	private final AiClient aiClient;

	private final SpotifySearchService spotifySearchService;

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
		List<String> artists = aiClient.fetchArtistList(concertName);

		List<AiClient.ArtistIdRecord> spotifyDetails = artists.stream()
			.filter(a -> a != null && !a.isBlank())
			.map(a -> spotifySearchService.searchArtist(a).orElse(null))
			.filter(java.util.Objects::nonNull)
			.toList();

		return ResponseEntity
			.ok(Map.of("concertName", concertName, "artistList", artists, "spotifyDetails", spotifyDetails));
	}

	@GetMapping("/run-kopis-and-ai-test")
	public ResponseEntity<?> runKopisAndAi() {
		List<Long> changedConcertIds = concertService.syncKopisData();
		var extracted = concertService.extractArtistsInfosWithAI(changedConcertIds);

		// TODO3 적용(artists upsert + concerts.casts 업데이트)
		concertCastApplyService.applyExtracted(extracted);

		return ResponseEntity.ok(Map.of("changedCount", changedConcertIds.size(), "extractedCount", extracted.size(),
				"results", extracted));
	}

}