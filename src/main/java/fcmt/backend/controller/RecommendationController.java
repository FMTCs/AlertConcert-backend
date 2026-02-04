package fcmt.backend.controller;

import fcmt.backend.dto.RecommendResponseDto;
import fcmt.backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	@GetMapping("/recommend")
	public ResponseEntity<RecommendResponseDto> getRecommendation(@RequestHeader("Authorization") String authHeader) {
		// 서비스 로직 호출 (DB 조회 -> 장르 추출 -> 공연 매칭)
		String accessToken = authHeader.substring(7);
		RecommendResponseDto response = recommendationService.getRecommendation(accessToken);

		return ResponseEntity.ok(response);
	}

	@PostMapping("/interest")
	public ResponseEntity<RecommendResponseDto> interest(@RequestHeader("Authorization") String authHeader) {
		String accessToken = authHeader.substring(7);
		RecommendResponseDto response = recommendationService.updatePreference(accessToken);

		return ResponseEntity.ok(response);
	}

}