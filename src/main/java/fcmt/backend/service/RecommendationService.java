package fcmt.backend.service;

import fcmt.backend.dto.ArtistItemDto;
import fcmt.backend.dto.RecommendResponseDto;
import fcmt.backend.dto.SpotifyTokenResponseDto;
import fcmt.backend.dto.SpotifyTopArtistsResponseDto;
import fcmt.backend.entity.Artist;
import fcmt.backend.entity.Concert;
import fcmt.backend.entity.User;
import fcmt.backend.entity.UserPreference;
import fcmt.backend.repository.ArtistRepository;
import fcmt.backend.repository.ConcertRepository;
import fcmt.backend.repository.UserPreferenceRepository;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.security.JwtTokenProvider;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

	private final UserPreferenceRepository userPreferenceRepository;

	private final ConcertRepository concertRepository;

	private final UserRepository userRepository;

	private final ArtistRepository artistRepository;

	private final JwtTokenProvider jwtTokenProvider;

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	private final RestTemplate restTemplate = new RestTemplate();

	@Transactional(readOnly = true)
	public RecommendResponseDto getRecommendation(String token) {
		Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
		UserPreference preference = userPreferenceRepository.findById(userId)
			.orElseThrow(() -> new EntityNotFoundException("선호도 정보가 없습니다."));

		List<Long> preferredIds = preference.getArtistIds();
		if (preferredIds == null || preferredIds.isEmpty()) {
			return RecommendResponseDto.builder()
				.topArtists(List.of())
				.topGenres(List.of())
				.recommendedConcerts(List.of())
				.build();
		}

		// 1. 선호 아티스트 상세 정보 조회
		List<Artist> preferredArtists = artistRepository.findAllById(preferredIds);

		// 2. 장르 빈도수 계산 및 Top Genres 추출
		Map<String, Long> genreFreqMap = preferredArtists.stream()
			.flatMap(a -> a.getGenres().stream())
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

		List<RecommendResponseDto.GenreDto> topGenres = genreFreqMap.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed()) // 많이 나타난 순서대로
			.limit(5) // 상위 10개
			.map(entry -> RecommendResponseDto.GenreDto.builder().name(entry.getKey()).build())
			.toList();

		// 3. 확장 추천을 위한 검색용 장르 리스트 (상위 5개 정도만 활용)
		List<String> searchGenres = topGenres.stream().map(RecommendResponseDto.GenreDto::getName).toList();

		// 4. 공연 검색 (선호 가수 포함 OR 선호 장르 포함)
		Long[] preferredIdsArray = preferredIds.toArray(new Long[0]);
		String[] searchGenresArray = searchGenres.toArray(new String[0]);

		List<Concert> matchedConcerts = concertRepository.findRecommendedConcerts(preferredIdsArray, searchGenresArray);

		// [최적화] 5-1. 매칭된 모든 공연의 출연진 ID를 중복 없이 수집
		Set<Long> allCastIds = matchedConcerts.stream().flatMap(c -> c.getCasts().stream()).collect(Collectors.toSet());

		// [최적화] 5-2. 필요한 아티스트 정보 한 번에 조회 (In-Query)
		Map<Long, Artist> artistMap = artistRepository.findAllById(allCastIds)
			.stream()
			.collect(Collectors.toMap(Artist::getArtistId, Function.identity()));

		// 5-3. 추천 점수 및 DTO 변환
		long totalUserGenreCount = Math.max(1, preferredArtists.stream().mapToLong(a -> a.getGenres().size()).sum());

		List<RecommendResponseDto.ConcertDto> concertDtos = matchedConcerts.stream().map(c -> {
			// [수정] ID 리스트를 Artist 객체 리스트로 변환
			List<Artist> casts = c.getCasts().stream().map(artistMap::get).filter(Objects::nonNull).toList();

			// [추가] 아티스트 객체에서 이름만 추출
			List<String> castNames = casts.stream().map(Artist::getArtistName).toList();

			List<String> concertGenres = casts.stream().flatMap(a -> a.getGenres().stream()).distinct().toList();

			// 매칭 점수 계산 로직
			double totalScore;

			// 1. 장르 점수 (최대 0.5)
			double genreScore = (concertGenres.stream().mapToDouble(g -> genreFreqMap.getOrDefault(g, 0L)).sum()
					/ (double) totalUserGenreCount) * 0.5;

			// 2. 아티스트 가점 (최대 0.5)
			boolean isFavoriteArtist = c.getCasts().stream().anyMatch(preferredIds::contains);
			double artistScore = isFavoriteArtist ? 0.5 : 0.0;

			totalScore = genreScore + artistScore;

			// 3. 매칭률 계산 (0점부터 시작하거나 기본 점수를 낮춤)
			// 40점(기본) + 60점(취향 반영)
			int matchingRate = (int) Math.round(50 + (totalScore * 50));

			return RecommendResponseDto.ConcertDto.builder()
				.concertName(c.getConcertName())
				.casts(castNames) // [수정] List<Long> 대신 List<String> 대입
				.genres(concertGenres)
				.posterImgUrl(c.getPosterImgUrl())
				.bookingUrl(c.getBookingUrl())
				.matchingRate(Math.min(100, matchingRate))
				.build();
		}).sorted(Comparator.comparingInt(RecommendResponseDto.ConcertDto::getMatchingRate).reversed()).toList();

		// 6. 결과 조립 (동일)
		return RecommendResponseDto.builder()
			.topArtists(preferredArtists.stream()
				.map(a -> RecommendResponseDto.ArtistDto.builder()
					.name(a.getArtistName())
					.artistId(a.getSpotifyArtistId())
					.build())
				.toList())
			.topGenres(topGenres)
			.recommendedConcerts(concertDtos)
			.build();
	}

	@Transactional
	public RecommendResponseDto updatePreference(String token) {
		try {
			Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
			User user = userRepository.findById(userId)
				.orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

			String refreshToken = user.getSpotifyRefreshTokenEnc();
			String accessToken = refreshAccessToken(refreshToken);

			List<ArtistItemDto> items = getTopArtists(accessToken);

			List<Long> artistPrimaryIds = new ArrayList<>();

			if (items != null && !items.isEmpty()) {
				for (ArtistItemDto item : items) {
					String spotifyId = item.getId();
					String name = item.getName();
					List<String> genres = item.getGenres();

					Artist artist = artistRepository.findBySpotifyArtistId(spotifyId).map(existing -> {
						existing.setArtistName(name);
						existing.setGenres(genres);
						return existing;
					})
						.orElseGet(() -> artistRepository
							.save(Artist.builder().spotifyArtistId(spotifyId).artistName(name).genres(genres).build()));
					artistPrimaryIds.add(artist.getArtistId());
				}
			}

			UserPreference preference = userPreferenceRepository.findById(userId)
				.orElseGet(() -> userPreferenceRepository.save(UserPreference.builder()
					.user(user)
					.artistIds(new ArrayList<>())
					.updatedAt(OffsetDateTime.now())
					.build()));

			preference.setArtistIds(artistPrimaryIds);
			preference.setUpdatedAt(OffsetDateTime.now());

			return getRecommendation(token);

		}
		catch (Exception e) {
			throw new RuntimeException("스포티파이 연동 업데이트 실패: " + e.getMessage());
		}
	}

	public String refreshAccessToken(String refreshToken) {
		String tokenUrl = "https://accounts.spotify.com/api/token";

		// 헤더 설정
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);

		// 바디 설정
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("refresh_token", refreshToken);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

		try {
			ResponseEntity<SpotifyTokenResponseDto> response = restTemplate.postForEntity(tokenUrl, request,
					SpotifyTokenResponseDto.class);

			if (response.getBody() != null) {
				return response.getBody().getAccessToken();
			}
		}
		catch (Exception e) {
			throw new RuntimeException("스포티파이 토큰 갱신 실패: " + e.getMessage());
		}

		return null;
	}

	public List<ArtistItemDto> getTopArtists(String accessToken) {
		String url = "https://api.spotify.com/v1/me/top/artists?time_range=medium_term&limit=50";

		// 헤더 설정
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<SpotifyTopArtistsResponseDto> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				SpotifyTopArtistsResponseDto.class);

		return Objects.requireNonNull(response.getBody()).getItems();
	}

}
