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

	private record UserTaste(Map<String, Double> specificFreq, Map<String, Double> categoryFreq) {}

	@Transactional(readOnly = true)
	public RecommendResponseDto getRecommendation(String token) {
		// 1. 유저 선호 아티스트 로드
		List<Long> preferredIds = getPreferredIds(token);
		if (preferredIds == null || preferredIds.isEmpty()) {
			return emptyRecommendResponse();
		}

		Map<Long, Artist> preferredArtistMap = getPreferredArtistMap(preferredIds);

		// 2. 유저 취향 분석
		UserTaste analysis = analyzeUserTaste(preferredIds, preferredArtistMap);

		// 3. 공연 검색
		List<Concert> matchedConcerts = searchConcerts(preferredIds, analysis.categoryFreq());

		// 4. 공연 DTO 변환
		List<RecommendResponseDto.ConcertDto> concertDtos = convertToConcertDtos(matchedConcerts, preferredIds, analysis);

		// 5. 최종 응답 조립
		return RecommendResponseDto.builder()
				.topArtists(preferredArtistsByOrder(preferredIds, preferredArtistMap))
				.topGenres(getTopGenres(analysis.specificFreq()))
				.recommendedConcerts(concertDtos)
				.build();
	}

	private List<Long> getPreferredIds(String token) {
		Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
        UserPreference preference = userPreferenceRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PREFERENCE_NOT_FOUND));

		return preference.getArtistIds();
	}

	// 유저 취향 장르 가중치 계산
	private UserTaste analyzeUserTaste(List<Long> preferredIds, Map<Long, Artist> artistMap) {
		Map<String, Double> specificFreq = new HashMap<>();
		Map<String, Double> categoryFreq = new HashMap<>();
		int totalCount = preferredIds.size();

		for (int i = 0; i < totalCount; i++) {
			Artist artist = artistMap.get(preferredIds.get(i));
			if (artist == null) continue;

			double rankWeight = (double) totalCount - i;
			Set<String> artistCategories = new HashSet<>();

			for (String genreFull : artist.getGenres()) {
				if (!genreFull.contains("/")) continue;
				specificFreq.put(genreFull, specificFreq.getOrDefault(genreFull, 0.0) + rankWeight);
				artistCategories.add(genreFull.split("/")[0]);
			}
			artistCategories.forEach(cat ->
					categoryFreq.put(cat, categoryFreq.getOrDefault(cat, 0.0) + rankWeight));
		}
		return new UserTaste(specificFreq, categoryFreq);
	}

	// 유저 선호 가수/장르와 일치하는 공연 검색
	private List<Concert> searchConcerts(List<Long> preferredIds, Map<String, Double> categoryFreq) {
		List<String> searchPatterns = categoryFreq.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(5)
				.map(e -> e.getKey() + "/%")
				.toList();

		return concertRepository.findRecommendedByCategories(
				preferredIds.toArray(new Long[0]),
				searchPatterns.toArray(new String[0])
		);
	}

	// 공연 리스트를 DTO 리스트로 변환
	private List<RecommendResponseDto.ConcertDto> convertToConcertDtos(
			List<Concert> concerts, List<Long> preferredIds, UserTaste taste) {

		// 출연진 캐싱
		Map<Long, Artist> artistMap = getAllArtistMapInConcerts(concerts);

		return concerts.stream().map(c -> {
					List<Artist> casts = c.getCasts().stream()
							.map(artistMap::get).filter(Objects::nonNull).toList();

					Set<String> cSpecs = new HashSet<>();
					Set<String> cCats = new HashSet<>();
					extractGenres(casts, cSpecs, cCats);

					int matchingRate = calculateMatchingRate(cSpecs, cCats, taste, preferredIds, c);

					return RecommendResponseDto.ConcertDto.builder()
							.concertName(c.getConcertName())
							.casts(casts.stream().map(Artist::getArtistName).toList())
							.genres(cSpecs.stream().map(s -> s.split("/")[1]).toList())
							.matchingRate(matchingRate)
							.posterImgUrl(c.getPosterImgUrl())
							.bookingUrl(c.getBookingUrl())
							.build();
				})
				.sorted(Comparator.comparingInt(RecommendResponseDto.ConcertDto::getMatchingRate).reversed())
				.toList();
	}

	// 각 가수로부터 장르 추출
	private void extractGenres(List<Artist> casts, Set<String> specs, Set<String> cats) {
		for (Artist cast : casts) {
			for (String g : cast.getGenres()) {
				if (g.contains("/")) {
					specs.add(g);
					cats.add(g.split("/")[0]);
				}
			}
		}
	}

	// 선호 가수 추출
	private Map<Long, Artist> getPreferredArtistMap(List<Long> ids) {
		return artistRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Artist::getArtistId, Function.identity()));
	}

	// 공연의 출연 가수 추출
	private Map<Long, Artist> getAllArtistMapInConcerts(List<Concert> concerts) {
		Set<Long> allCastIds = concerts.stream().flatMap(c -> c.getCasts().stream()).collect(Collectors.toSet());
		return artistRepository.findAllById(allCastIds).stream()
				.collect(Collectors.toMap(Artist::getArtistId, Function.identity()));
	}

	// 빈 DTO 반환
	private RecommendResponseDto emptyRecommendResponse() {
		return RecommendResponseDto.builder()
				.topArtists(List.of()).topGenres(List.of()).recommendedConcerts(List.of())
				.build();
	}

	// 매칭률 계산
	private int calculateMatchingRate(Set<String> cSpecs, Set<String> cCats,
									  UserTaste usertaste, List<Long> preferredIds, Concert c) {
		List<Double> genreMatchScores = new ArrayList<>();

		for (String s : cSpecs) {
			double score = usertaste.specificFreq.getOrDefault(s, 0.0) * 0.5; // 소분류 가중치
			if (score > 0) genreMatchScores.add(score);
		}
		for (String cat : cCats) {
			double score = usertaste.categoryFreq.getOrDefault(cat, 0.0) * 0.1; // 중분류 가중치
			if (score > 0) genreMatchScores.add(score);
		}

		// 점수가 높은 장르 3개만 합산
		double topMatchWeight = genreMatchScores.stream()
				.sorted(Comparator.reverseOrder())
				.limit(3)
				.mapToDouble(Double::doubleValue)
				.sum();

		double genreScore = Math.min(1.0, topMatchWeight / 400.0);

		boolean isFavorite = c.getCasts().stream().anyMatch(preferredIds::contains);
		int matchingRate;

		if(isFavorite)
			matchingRate = (int) Math.round(90 + (genreScore * 10)); // 선호 아티스트가 있는 경우
		else
			matchingRate = (int) Math.round(40 + (genreScore * 55)); // 선호 아티스트가 없는 경우

		return matchingRate;
	}

	// 선호하는 아티스트 5명 반환
	private List<RecommendResponseDto.ArtistDto> preferredArtistsByOrder(List<Long> ids, Map<Long, Artist> map) {
		return ids.stream()
				.filter(map::containsKey)
				.limit(5)
				.map(id -> RecommendResponseDto.ArtistDto.builder().name(map.get(id).getArtistName()).build())
				.toList();
	}

	// 선호하는 장르 5개 반환
	private List<RecommendResponseDto.GenreDto> getTopGenres(Map<String, Double> freq) {
		return freq.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(5)
				.map(e -> RecommendResponseDto.GenreDto.builder()
						.name(e.getKey().split("/")[1])
						.build())
				.toList();
	}

	@Transactional
	public void updatePreference(String token) {
		try {
			// 1. 유저 및 Spotify 액세스 토큰 준비
			User user = getUserByToken(token);
			String accessToken = refreshAccessToken(user.getSpotifyRefreshTokenEnc());

			// 2. Spotify 데이터 가져오기
			List<ArtistItemDto> spotifyItems = getTopArtists(accessToken);
			if (spotifyItems == null || spotifyItems.isEmpty()) return;

			// AI 분류가 필요한 ID들만 담을 리스트 생성
			List<Long> needsAiClassification = new ArrayList<>();

			// 3. 아티스트 정보 DB 동기화 및 ID 추출
			List<Long> artistPrimaryIds = syncArtistsWithDb(spotifyItems, needsAiClassification);

			// 4. AI 장르 분류 서비스 호출
			if (!needsAiClassification.isEmpty()) {
				artistGenreService.classifyAndUpdateGenres(needsAiClassification);
			}

			// 5. 유저 최종 선호도 정보 업데이트
			saveUserPreference(user, artistPrimaryIds);

		} catch (HttpClientErrorException.Unauthorized e) {
			throw new BusinessException(ErrorCode.SESSION_EXPIRED);
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 동기화 중 오류가 발생했습니다.");
		}
	}

	// 유저 정보 조회
	private User getUserByToken(String token) {
		Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
		return userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	// AccessToken 재발급
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

	// 선호 아티스트 가져오기
	public List<ArtistItemDto> getTopArtists(String accessToken) {
		String url = "https://api.spotify.com/v1/me/ㄱtop/artists?time_range=medium_term&limit=50";

		// 헤더 설정
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<SpotifyTopArtistsResponseDto> response = restTemplate.exchange(url, HttpMethod.GET, entity,
				SpotifyTopArtistsResponseDto.class);

		return Objects.requireNonNull(response.getBody()).getItems();
	}

	// artists DB 업데이트
	private List<Long> syncArtistsWithDb(List<ArtistItemDto> items, List<Long> needsAiClassification) {
		List<Long> allArtistPrimaryIds = new ArrayList<>();

		for (ArtistItemDto item : items) {
			Artist artist = artistRepository.findBySpotifyArtistId(item.getId())
					.map(existing -> {
						existing.setArtistName(item.getName());
						return existing;
					})
					.orElseGet(() -> artistRepository.save(Artist.builder()
							.spotifyArtistId(item.getId())
							.artistName(item.getName())
							.genres(item.getGenres()) // 초기에는 Spotify 장르 저장
							.build()));

			allArtistPrimaryIds.add(artist.getArtistId());

			// AI 분류가 필요한지 체크
			if (shouldClassifyWithAi(artist)) {
				needsAiClassification.add(artist.getArtistId());
			}
		}
		return allArtistPrimaryIds;
	}

	// AI를 사용해야 하는지 확인
	private boolean shouldClassifyWithAi(Artist artist) {
		List<String> currentGenres = artist.getGenres();

		// 1. 장르 데이터가 아예 없는 경우
		if (currentGenres == null || currentGenres.isEmpty()) return true;

		// 2. 장르에 우리가 정의한 구분자("/")가 하나도 없는 경우 (Spotify 원시 데이터인 상태)
		return currentGenres.stream().noneMatch(g -> g.contains("/"));
	}

	// 유저 선호도 저장
	private void saveUserPreference(User user, List<Long> artistIds) {
		UserPreference preference = userPreferenceRepository.findById(user.getUid())
				.orElseGet(() -> userPreferenceRepository.save(UserPreference.builder()
						.user(user)
						.artistIds(new ArrayList<>())
						.build()));

		preference.setArtistIds(artistIds);
		preference.setUpdatedAt(OffsetDateTime.now());
	}

}
