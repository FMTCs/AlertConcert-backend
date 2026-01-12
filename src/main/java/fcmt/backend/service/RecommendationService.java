package fcmt.backend.service;

import fcmt.backend.dto.RecommendResponseDto;
import fcmt.backend.entity.Concert;
import fcmt.backend.entity.UserPreference;
import fcmt.backend.repository.ConcertRepository;
import fcmt.backend.repository.UserPreferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

	private final UserPreferenceRepository userPreferenceRepository;

	private final ConcertRepository concertRepository;

	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked") // 형변환 경고 무시
	public RecommendResponseDto getRecommendation(Long userId) {
		// 1. UserPreference 조회
		UserPreference preference = userPreferenceRepository.findById(userId)
			.orElseThrow(() -> new EntityNotFoundException("선호도 정보가 없습니다. ID: " + userId));

		Map<String, Object> rawPref = preference.getPreference();
		List<Map<String, Object>> items = (List<Map<String, Object>>) rawPref.get("items");

		if (items == null || items.isEmpty()) {
			return RecommendResponseDto.builder().topArtists(List.of()).recommendedConcerts(List.of()).build();
		}

		// 2. 유저 선호 아티스트 ID 추출 (Spotify ID 기반 식별용)
		Set<String> preferredArtistIds = items.stream()
			.map(item -> (String) item.get("id"))
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		// 3. ArtistDto 리스트 생성 및 전체 장르 수집
		List<RecommendResponseDto.ArtistDto> topArtists = items.stream()
			.map(item -> RecommendResponseDto.ArtistDto.builder()
				.name((String) item.get("name"))
				.genres((List<String>) item.get("genres")) // Object -> List<String>
				.build())
			.collect(Collectors.toList());

		// 추천에 사용할 장르 합치기
		List<String> allPreferredGenres = topArtists.stream()
			.filter(artist -> artist.getGenres() != null)
			.flatMap(artist -> artist.getGenres().stream())
			.toList();

		Map<String, Long> genreFrequencyMap = allPreferredGenres.stream()
			.collect(Collectors.groupingBy(String::toString, Collectors.counting()));

		long totalFrequencyCount = allPreferredGenres.isEmpty() ? 1 : allPreferredGenres.size();

		// 4. 공연 조회
		String[] genreArray = genreFrequencyMap.keySet().toArray(new String[0]);
		String[] artistIdArray = preferredArtistIds.toArray(new String[0]);

		List<Concert> matchedConcerts = concertRepository.findByGenresIn(genreArray, artistIdArray);

		// 5. 공연별 매칭률 계산 및 DTO 변환
		List<RecommendResponseDto.ConcertDto> concertDtos = matchedConcerts.stream().map(c -> {
			// 1. 실제 장르 일치 점수 계산 (0.0 ~ 1.0)
			double rawScore = c.getGenres().stream().mapToDouble(g -> genreFrequencyMap.getOrDefault(g, 0L)).sum()
					/ (double) totalFrequencyCount;

			boolean hasFavoriteArtist = c.getCasts()
				.stream()
				.anyMatch(castMap -> preferredArtistIds.contains((String) castMap.get("id")));

			double artistBonus = hasFavoriteArtist ? 0.7 : 0.0;
			double totalScore = Math.min(1.0, rawScore + artistBonus);

			int matchingRate = (int) Math.round(70 + (totalScore * 30));
			matchingRate = Math.min(100, matchingRate);

			return RecommendResponseDto.ConcertDto.builder()
				.concertName(c.getConcertName())
				.casts(c.getCasts())
				.genres(c.getGenres())
				.posterImgUrl(c.getPosterImgUrl())
				.bookingUrl(c.getBookingUrl())
				.matchingRate(matchingRate)
				.build();
		}).sorted((a, b) -> Integer.compare(b.getMatchingRate(), a.getMatchingRate())).collect(Collectors.toList());

		// 6. 결과 반환
		return RecommendResponseDto.builder().topArtists(topArtists).recommendedConcerts(concertDtos).build();
	}

}