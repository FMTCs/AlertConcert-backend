package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.entity.Artist;
import fcmt.backend.entity.Concert;
import fcmt.backend.repository.ArtistRepository;
import fcmt.backend.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertCastApplyService {

	private final ArtistRepository artistRepository;

	private final ConcertRepository concertRepository;

	/**
	 * extracted 결과를 바탕으로: 1) artists upsert(spotify_artist_id unique) 2) concerts.casts =
	 * artist_id[] 업데이트
	 */
	@Transactional
	public void applyExtracted(List<ConcertService.ConcertArtistExtractResult> extracted) {
		if (extracted == null || extracted.isEmpty()) {
			log.info("AI 추출 결과가 없어 DB 반영을 생략합니다.");
			return;
		}

		// 같은 실행 내 중복 조회 최소화
		Map<String, Long> spotifyIdToArtistIdCache = new HashMap<>();

		int insertedArtists = 0;
		int updatedArtistNames = 0;
		int updatedConcerts = 0;
		int skippedConcerts = 0;

		for (var r : extracted) {
			Long concertId = r.concertId();
			String concertName = r.concertName();
			List<AiClient.ArtistIdRecord> spotifyDetails = (r.spotifyDetails() == null) ? List.of()
					: r.spotifyDetails();

			if (spotifyDetails.isEmpty()) {
				log.warn("콘서트 출연진 추출 결과가 비어 있어 casts 업데이트를 스킵합니다. (concert_id={}, name='{}')", concertId, concertName);
				skippedConcerts++;
				continue;
			}

			// (1) spotify_artist_id -> artist_id 확보 (upsert)
			LinkedHashSet<Long> artistIdSet = new LinkedHashSet<>();

			for (AiClient.ArtistIdRecord d : spotifyDetails) {
				if (d == null)
					continue;

				String spotifyArtistId = getSpotifyArtistId(d);
				String artistName = d.name();

				if (spotifyArtistId == null || spotifyArtistId.isBlank())
					continue;
				if (artistName == null || artistName.isBlank())
					continue;

				Long artistId = spotifyIdToArtistIdCache.get(spotifyArtistId);
				if (artistId == null) {
					UpsertResult up = upsertArtist(spotifyArtistId, artistName);
					artistId = up.artistId();

					spotifyIdToArtistIdCache.put(spotifyArtistId, artistId);
					insertedArtists += up.inserted ? 1 : 0;
					updatedArtistNames += up.updatedName ? 1 : 0;
				}

				if (artistId != null) {
					artistIdSet.add(artistId);
				}
			}

			if (artistIdSet.isEmpty()) {
				log.warn("유효한 artist_id를 만들지 못해 casts 업데이트를 스킵합니다. (concert_id={}, name='{}')", concertId, concertName);
				skippedConcerts++;
				continue;
			}

			// (2) concerts.casts 업데이트 (변경 없으면 save 생략)
			Concert concert = concertRepository.findById(concertId).orElse(null);
			if (concert == null) {
				log.warn("콘서트를 찾지 못해 casts 업데이트를 스킵합니다. (concert_id={}, name='{}')", concertId, concertName);
				skippedConcerts++;
				continue;
			}

			List<Long> newCasts = new ArrayList<>(artistIdSet);

			if (Objects.equals(concert.getCasts(), newCasts)) {
				log.info("casts 변경 없음. (concert_id={}, name='{}', castsSize={})", concertId, concertName,
						newCasts.size());
				continue;
			}

			concert.setCasts(newCasts);
			concertRepository.save(concert);
			updatedConcerts++;

			log.info("casts 업데이트 완료. (concert_id={}, name='{}', castsSize={}, casts={})", concertId, concertName,
					newCasts.size(), newCasts);
		}

		log.info("DB 반영 요약: concertsUpdated={}, concertsSkipped={}, artistsInserted={}, artistNameUpdated={}",
				updatedConcerts, skippedConcerts, insertedArtists, updatedArtistNames);
	}

	private String getSpotifyArtistId(AiClient.ArtistIdRecord d) {
		return d.spotify_id();
	}

	private record UpsertResult(Long artistId, boolean inserted, boolean updatedName) {
	}

	private UpsertResult upsertArtist(String spotifyArtistId, String artistName) {
		// 있으면 사용 (+ 필요 시 name만 갱신)
		Optional<Artist> existingOpt = artistRepository.findBySpotifyArtistId(spotifyArtistId);
		if (existingOpt.isPresent()) {
			Artist a = existingOpt.get();

			boolean updatedName = false;
			if (!Objects.equals(a.getArtistName(), artistName)) {
				a.setArtistName(artistName);
				artistRepository.save(a);
				updatedName = true;
			}

			return new UpsertResult(a.getArtistId(), false, updatedName);
		}

		// 없으면 insert (genres는 일단 빈 배열)
		try {
			Artist created = Artist.builder()
				.spotifyArtistId(spotifyArtistId)
				.artistName(artistName)
				.genres(List.of()) // TEXT[] NOT NULL default 있는데, 엔티티가 null 허용 안 하면 빈
									// 리스트로
				.build();

			Artist saved = artistRepository.save(created);
			return new UpsertResult(saved.getArtistId(), true, false);
		}
		catch (DataIntegrityViolationException e) {
			// 동시 insert 레이스 대비: 다시 조회해서 id 반환
			return artistRepository.findBySpotifyArtistId(spotifyArtistId)
				.map(a -> new UpsertResult(a.getArtistId(), false, false))
				.orElse(new UpsertResult(null, false, false));
		}
	}

}
