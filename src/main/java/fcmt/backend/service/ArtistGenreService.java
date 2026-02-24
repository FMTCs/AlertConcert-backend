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

	public void classifyAndUpdateGenres(List<Long> artistIds) {
		if (artistIds == null || artistIds.isEmpty())
			return;

		List<Artist> artists = artistRepository.findAllById(artistIds);
		if (artists.isEmpty())
			return;

		for (Artist a : artists) {
			var res = aiClient.fetchGenresWithAi(a.getSpotifyArtistId(), a.getArtistName());
			a.setGenres(res.genres());
		}

		// 저장만 트랜잭션
		saveAllTx(artists);

		log.info("장르 업데이트 완료: targetArtists={}", artists.size());
	}

	// saveAll만 트랜잭션
	@Transactional
	protected void saveAllTx(List<Artist> artists) {
		artistRepository.saveAll(artists);
	}

}