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

		// 2. "items" 추출 (Object -> List<Map>)
		List<Map<String, Object>> items = (List<Map<String, Object>>) rawPref.get("items");

		if (items == null) {
			return RecommendResponseDto.builder().topArtists(List.of()).recommendedConcerts(List.of()).build();
		}

		// 3. ArtistDto 리스트 생성 및 전체 장르 수집
		List<RecommendResponseDto.ArtistDto> topArtists = items.stream()
			.map(item -> RecommendResponseDto.ArtistDto.builder()
				.name((String) item.get("name"))
				.genres((List<String>) item.get("genres")) // Object -> List<String>
				.build())
			.collect(Collectors.toList());

		// 추천에 사용할 장르 합치기 (중복 제거)
		List<String> allPreferredGenres = topArtists.stream()
			.filter(artist -> artist.getGenres() != null)
			.flatMap(artist -> artist.getGenres().stream())
			.distinct()
			.toList();

		// 4. 공연 조회
		String[] genreArray = allPreferredGenres.toArray(new String[0]);
		List<Concert> matchedConcerts = concertRepository.findByGenresIn(genreArray);

		// 5. 결과 반환
		return RecommendResponseDto.builder()
			.topArtists(topArtists)
			.recommendedConcerts(matchedConcerts.stream()
				.map(c -> RecommendResponseDto.ConcertDto.builder()
					.concertName(c.getConcertName())
					.genres(c.getGenres())
					.posterImgUrl(c.getPosterImgUrl())
					.bookingUrl(c.getBookingUrl())
					.build())
				.collect(Collectors.toList()))
			.build();
	}

}