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
		// 2. 주입된 aiClient를 그대로 사용합니다.
		var artists = aiClient.fetchArtistList(concertName,
				"http://www.kopis.or.kr/upload/pfmPoster/PF_PF283372_260116_155717.gif");

		AiClient.ArtistIdRecord artistId = null;
		if (artists != null && !artists.isEmpty()) {
			artistId = aiClient.fetchSpotifyIdByArtistName(artists.getFirst());
		}

		return ResponseEntity.ok(Map.of("artistList", artists != null ? artists : "검색 결과 없음", "spotifyIdCheck",
				artistId != null ? artistId : "ID 찾지 못함"));
	}

}