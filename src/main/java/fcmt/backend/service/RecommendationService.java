package fcmt.backend.service;

import fcmt.backend.dto.*;
import fcmt.backend.entity.Artist;
import fcmt.backend.entity.Concert;
import fcmt.backend.entity.User;
import fcmt.backend.entity.UserPreference;
import fcmt.backend.exception.BusinessException;
import fcmt.backend.exception.ErrorCode;
import fcmt.backend.repository.ArtistRepository;
import fcmt.backend.repository.ConcertRepository;
import fcmt.backend.repository.UserPreferenceRepository;
import fcmt.backend.repository.UserRepository;
import fcmt.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private final UserPreferenceRepository userPreferenceRepository;

	private final ConcertRepository concertRepository;

	private final UserRepository userRepository;

	private final ArtistRepository artistRepository;

	private final JwtTokenProvider jwtTokenProvider;

	private final ArtistGenreService artistGenreService;

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	private final RestTemplate restTemplate = new RestTemplate();

	public RecommendResponseDto getRecommendation(String token) {
		// 1. 유저 및 선호 아티스트 조회
		Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
		UserPreference preference = userPreferenceRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_PREFERENCE_NOT_FOUND));

		List<Long> preferredIds = preference.getArtistIds();
		if (preferredIds == null || preferredIds.isEmpty()) {
			return RecommendResponseDto.builder()
				.topArtists(List.of())
				.topGenres(List.of())
				.recommendedConcerts(List.of())
				.build();
		}

		List<Artist> preferredArtists = artistRepository.findAllById(preferredIds);

		// 2. 취향 분석 (중분류/소분류 빈도 계산)
		Map<String, Long> specificFreq = new HashMap<>(); // "Idol_Concepts/Teen_Fresh"
		Map<String, Long> categoryFreq = new HashMap<>(); // "Idol_Concepts"

		for (Artist artist : preferredArtists) {
			Set<String> artistCategories = new HashSet<>();
			for (String genreFull : artist.getGenres()) {
				specificFreq.put(genreFull, specificFreq.getOrDefault(genreFull, 0L) + 1);

				String category = genreFull.split("/")[0]; // "/" 기준 앞부분 추출
				artistCategories.add(category);
			}
			// 가수당 중분류 가중치는 1회만 합산
			artistCategories.forEach(cat -> categoryFreq.put(cat, categoryFreq.getOrDefault(cat, 0L) + 1));
		}

		// 3. 검색 키워드 생성 (상위 5개 중분류 기반 LIKE 패턴)
		List<String> searchPatterns = categoryFreq.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
			.limit(5)
			.map(e -> e.getKey() + "/%") // "중분류/%" 형태로 변환
			.toList();

		// 4. 공연 검색
		List<Concert> matchedConcerts = concertRepository.findRecommendedByCategories(preferredIds.toArray(new Long[0]),
				searchPatterns.toArray(new String[0]));

		// 출연진 정보 캐싱 (성능 최적화)
		Set<Long> allCastIds = matchedConcerts.stream().flatMap(c -> c.getCasts().stream()).collect(Collectors.toSet());
		Map<Long, Artist> artistMap = artistRepository.findAllById(allCastIds)
			.stream()
			.collect(Collectors.toMap(Artist::getArtistId, Function.identity()));

		List<RecommendResponseDto.ConcertDto> concertDtos = matchedConcerts.stream().map(c -> {
			List<Artist> casts = c.getCasts().stream().map(artistMap::get).filter(Objects::nonNull).toList();

			Set<String> cSpecs = new HashSet<>();
			Set<String> cCats = new HashSet<>();
			for (Artist cast : casts) {
				for (String g : cast.getGenres()) {
					cSpecs.add(g);
					cCats.add(g.split("/")[0]);
				}
			}

			// 1. 장르별 일치 가중치 합산
			double matchWeight = 0.0;

			for (String s : cSpecs) {
				if (specificFreq.containsKey(s)) {
					matchWeight += specificFreq.get(s) * 1.5; // 소분류 일치시 강력 가점
				}
			}
			for (String cat : cCats) {
				if (categoryFreq.containsKey(cat)) {
					matchWeight += categoryFreq.get(cat) * 0.7; // 중분류 일치 가점
				}
			}

			double genreScore = Math.min(1.0, matchWeight / 15.0); // 15점만 넘어도 장르 점수
																	// 만점(1.0)
			genreScore = Math.pow(genreScore, 0.5); // 루트를 씌워 하위권 점수를 상향 평준화

			// 3. 최종 점수 조립 (기본 50 + 장르 35 + 선호아티스트 15)
			boolean isFavorite = c.getCasts().stream().anyMatch(preferredIds::contains);
			int matchingRate;

			if (isFavorite) {
				matchingRate = 95;
			}
			else {
				matchingRate = (int) Math.round(50 + (genreScore * 35));
			}

			matchingRate = Math.min(100, matchingRate);

			return RecommendResponseDto.ConcertDto.builder()
				.concertName(c.getConcertName())
				.casts(casts.stream().map(Artist::getArtistName).toList())
				.genres(cSpecs.stream().map(s -> s.split("/")[1]).toList()) // 소분류만 노출
				.matchingRate(Math.min(100, matchingRate))
				.posterImgUrl(c.getPosterImgUrl())
				.bookingUrl(c.getBookingUrl())
				.build();
		}).sorted(Comparator.comparingInt(RecommendResponseDto.ConcertDto::getMatchingRate).reversed()).toList();

		return RecommendResponseDto.builder()
			.topArtists(preferredArtists.stream()
				.map(a -> RecommendResponseDto.ArtistDto.builder().name(a.getArtistName()).build())
				.toList())
			.topGenres(specificFreq.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(5)
				.map(e -> RecommendResponseDto.GenreDto.builder()
					.name(e.getKey().split("/")[1]) // 소분류만 추출
					.build())
				.toList())
			.recommendedConcerts(concertDtos) // 이미 빌더로 생성된 리스트
			.build();
	}

	@Transactional
	public void updatePreference(String token) {
		try {
			Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
			User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

			String refreshToken = user.getSpotifyRefreshTokenEnc();
			String accessToken = refreshAccessToken(refreshToken);

			List<ArtistItemDto> items = getTopArtists(accessToken);
			List<Long> artistPrimaryIds = new ArrayList<>();

			if (items != null && !items.isEmpty()) {
				for (ArtistItemDto item : items) {
					String spotifyId = item.getId();
					String name = item.getName();
					List<String> spotifyGenres = item.getGenres();

					Artist artist = artistRepository.findBySpotifyArtistId(spotifyId).map(existing -> {
						existing.setArtistName(name);
						return existing;
					})
						.orElseGet(() -> artistRepository.save(Artist.builder()
							.spotifyArtistId(spotifyId)
							.artistName(name)
							.genres(spotifyGenres)
							.build()));
					artistPrimaryIds.add(artist.getArtistId());
				}

				artistGenreService.classifyAndUpdateGenres(artistPrimaryIds);
			}

			UserPreference preference = userPreferenceRepository.findById(userId)
				.orElseGet(() -> userPreferenceRepository.save(UserPreference.builder()
					.user(user)
					.artistIds(new ArrayList<>())
					.updatedAt(OffsetDateTime.now())
					.build()));

			preference.setArtistIds(artistPrimaryIds);
			preference.setUpdatedAt(OffsetDateTime.now());

		}
		catch (HttpClientErrorException.Unauthorized e) {
			throw new BusinessException(ErrorCode.SESSION_EXPIRED);
		}
		catch (Exception e) {
			log.error("Preference Update Error: ", e);
			throw new RuntimeException(e);
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

			SpotifyTokenResponseDto body = response.getBody();

			if (body == null || body.getAccessToken() == null) {
				throw new BusinessException(ErrorCode.SPOTIFY_INVALID_TOKEN);
			}
			return body.getAccessToken();
		}
		catch (HttpClientErrorException e) {
			// 4xx 에러 (토큰 만료, 클라이언트 정보 불일치 등)
			log.warn("Spotify API Client Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

			// 리프레시 토큰 자체가 만료된 경우 세션 만료로 처리
			if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.BAD_REQUEST) {
				throw new BusinessException(ErrorCode.SESSION_EXPIRED);
			}
			throw new BusinessException(ErrorCode.SPOTIFY_API_ERROR);

		}
		catch (Exception e) {
			// 5xx 에러 또는 네트워크 타임아웃
			log.error("Spotify API Connection Error: ", e);
			throw new BusinessException(ErrorCode.SPOTIFY_API_ERROR);
		}
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
