package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.entity.Artist;
import fcmt.backend.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistGenreService {

	private final ArtistRepository artistRepository;

	private final AiClient aiClient;

	@Transactional // 전체를 트랜잭션으로 묶어 변경 감지(Dirty Checking) 활용
	public void classifyAndUpdateGenres(List<Long> artistIds) {
		if (artistIds == null || artistIds.isEmpty())
			return;

		List<Artist> artists = artistRepository.findAllById(artistIds);

		for (Artist a : artists) {
			// 이미 우리가 만든 형식(중분류/소분류)이 저장되어 있는지 확인 (선택 사항)
			// if (a.getGenres() != null && a.getGenres().stream().anyMatch(g ->
			// g.contains("/"))) continue;

			try {
				var res = aiClient.fetchGenresWithAi(a.getSpotifyArtistId(), a.getArtistName());
				// AI 응답 res.genres()는 ["Idol_Concepts/Teen_Fresh", ...] 형식이어야 함
				a.setGenres(res.genres());
			}
			catch (Exception e) {
				log.error("AI 장르 분류 실패 (Artist: {}): {}", a.getArtistName(), e.getMessage());
				// 실패 시 기본값이라도 넣어 ArrayIndexOutOfBounds 방지
				a.setGenres(List.of("Unknown/Others"));
			}
		}
		// @Transactional 덕분에 별도의 save 호출 없이도 메서드 종료 시 DB에 반영됨
	}

}