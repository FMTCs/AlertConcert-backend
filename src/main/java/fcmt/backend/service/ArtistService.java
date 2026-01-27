package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.dto.ArtistAiResponseDto;
import fcmt.backend.entity.Artist;
import fcmt.backend.repository.ArtistRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistService {

	private final ArtistRepository artistRepository;

	private final AiClient aiClient;

	@Transactional
	public List<Long> addAndGetArtistIds(List<Map<String, String>> artistList) {
		List<Long> castIds = new ArrayList<>();
		List<Map<String, String>> unKnownArtists = new ArrayList<>();

		for (Map<String, String> artistInfo : artistList) {
			String spotifyArtistId = artistInfo.get("spotify_artist_id");
			String artistName = artistInfo.get("artist_name");

			// 1. Artists 테이블 탐색
			Optional<Artist> artistEntity = artistRepository.findBySpotifyArtistId(spotifyArtistId);

			// 2. 존재 여부 확인
			if (artistEntity.isPresent()) {
				// 2-1. 존재하면 Concerts db의 casts에 추가
				log.info(">>> 이미 존재하는 가수 입니다! : {}", artistName);
				Long existingArtistId = artistEntity.get().getArtistId();
				castIds.add(existingArtistId);
			}
			else {
				// 2-2. 존재하지 않으면 List에 추가
				unKnownArtists.add(artistInfo);
			}
		}
		// 3. 존재하지 않는 Artists AI 처리 및 일괄 저장
		if (!unKnownArtists.isEmpty()) {
			// 3-1. 중복 제거 -> [TODO] 해당 부분 refactoring 필요
			List<Map<String, String>> distinctUnknowns = new ArrayList<>(unKnownArtists.stream()
				.collect(Collectors.toMap(m -> m.get("spotify_artist_id"), m -> m, (existing, replacement) -> existing))
				.values());

			// 3-2. Ai 사용으로 장르 정보 가져오기
			log.info(">>> {}명의 신규 가수 정보를 AI로부터 가져옵니다.", distinctUnknowns.size());
			List<ArtistAiResponseDto> newArtistDtos = aiClient.fetchGenresFromAi(distinctUnknowns);

			// 3-3 해당 정보들로 entity 만들기
			List<Artist> artists = newArtistDtos.stream()
				.map(this::convertToEntity) // 별도 메서드로 분리 (클린 코드)
				.toList();

			// 3-4. db에 저장
			List<Artist> savedArtists = artistRepository.saveAll(artists);

			// 3-5. 새로 생성된 id를 가져와서 추가
			for (Artist saved : savedArtists) {
				Long artistId = saved.getArtistId();
				castIds.add(artistId);
			}
			log.info(">>> 신규 가수 {}명 저장 완료.", castIds.size());
		}

		// 4. 새로 추가된 아티스트 id를 넘겨서 concert DB에서 저장
		return castIds;
	}

	private Artist convertToEntity(ArtistAiResponseDto dto) {
		return Artist.builder()
			.artistName(dto.getArtistName())
			.spotifyArtistId(dto.getSpotifyArtistId())
			.genres(dto.getGenres())
			.build();
	}

}
