package fcmt.backend.controller;

import fcmt.backend.dto.RecommendResponseDto;
import fcmt.backend.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

	private final RecommendationService recommendationService;

	@GetMapping("/recommend")
	public ResponseEntity<RecommendResponseDto> getRecommendation(@RequestParam(name = "userId") Long userId) {
		// 서비스 로직 호출 (DB 조회 -> 장르 추출 -> 공연 매칭)
		RecommendResponseDto response = recommendationService.getRecommendation(userId);

		return ResponseEntity.ok(response);
	}

}