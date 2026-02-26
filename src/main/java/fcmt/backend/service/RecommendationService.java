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
import jakarta.persistence.EntityNotFoundException;
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

	@Value("${spotify.api.client-id}")
	private String clientId;

	@Value("${spotify.api.client-secret}")
	private String clientSecret;

	private final RestTemplate restTemplate = new RestTemplate();

	@Transactional(readOnly = true)
	public RecommendResponseDto getRecommendation(String token) {
		// 1. 유저 선호도 조회
		Long userId = jwtTokenProvider.parseClaimsAllowExpired(token).get("uid", Long.class);
		UserPreference preference = userPreferenceRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_PREFERENCE_NOT_FOUND));

		List<Long> preferredIds = preference.getArtistIds();
		if (preferredIds == null || preferredIds.isEmpty()) {
			return RecommendResponseDto.builder().topArtists(List.of()).topGenres(List.of()).recommendedConcerts(List.of()).build();
		}

		// 2. 선호 아티스트 상세 조회
		List<Artist> preferredArtists = artistRepository.findAllById(preferredIds);

		// 3. 유저 취향 계층별 분석
		Map<String, Long> specificFreq = new HashMap<>();
		Map<String, Long> categoryFreq = new HashMap<>();
		Map<String, Long> domainFreq = new HashMap<>();

		for (Artist artist : preferredArtists) {
			Set<String> categoriesForArtist = new HashSet<>();
			Set<String> domainsForArtist = new HashSet<>();

			for (String specific : artist.getGenres()) {
				specificFreq.put(specific, specificFreq.getOrDefault(specific, 0L) + 1);
				GenreMapper.GenreInfo info = GenreMapper.getInfo(specific);
				if (!info.getCategory().equals("Unknown")) categoriesForArtist.add(info.getCategory());
				if (!info.getDomain().equals("Unknown")) domainsForArtist.add(info.getDomain());
			}
			// 아티스트당 중/대분류는 1회만 카운트
			categoriesForArtist.forEach(c -> categoryFreq.put(c, categoryFreq.getOrDefault(c, 0L) + 1));
			domainsForArtist.forEach(d -> domainFreq.put(d, domainFreq.getOrDefault(d, 0L) + 1));
		}

		// 4. 상위 10개 소분류 추출 (DB 검색용)
		List<RecommendResponseDto.GenreDto> topGenreDtos = specificFreq.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(10)
				.map(e -> RecommendResponseDto.GenreDto.builder().name(e.getKey()).build()).toList();

		// 5. 공연 검색
		List<Concert> matchedConcerts = concertRepository.findRecommendedConcerts(
				preferredIds.toArray(new Long[0]),
				topGenreDtos.stream().map(RecommendResponseDto.GenreDto::getName).toArray(String[]::new)
		);

		// 6. 데이터 최적화 로딩
		Set<Long> allCastIds = matchedConcerts.stream().flatMap(c -> c.getCasts().stream()).collect(Collectors.toSet());
		Map<Long, Artist> artistMap = artistRepository.findAllById(allCastIds).stream()
				.collect(Collectors.toMap(Artist::getArtistId, Function.identity()));

		// 7. 분모 계산 (유저 총 취향 무게)
		double userTotalWeight = (specificFreq.values().stream().mapToLong(Long::longValue).sum() * 0.5)
				+ (categoryFreq.values().stream().mapToLong(Long::longValue).sum() * 0.3)
				+ (domainFreq.values().stream().mapToLong(Long::longValue).sum() * 0.2);

		// 8. 공연별 점수 계산
		return RecommendResponseDto.builder()
				.topArtists(preferredArtists.stream().map(a -> RecommendResponseDto.ArtistDto.builder().name(a.getArtistName()).artistId(a.getSpotifyArtistId()).build()).toList())
				.topGenres(topGenreDtos)
				.recommendedConcerts(matchedConcerts.stream().map(c -> {
					List<Artist> casts = c.getCasts().stream().map(artistMap::get).filter(Objects::nonNull).toList();

					Set<String> cSpecs = new HashSet<>();
					Set<String> cCats = new HashSet<>();
					Set<String> cDoms = new HashSet<>();

					for (Artist cast : casts) {
						for (String s : cast.getGenres()) {
							cSpecs.add(s);
							GenreMapper.GenreInfo info = GenreMapper.getInfo(s);
							cCats.add(info.getCategory());
							cDoms.add(info.getDomain());
						}
					}

					double matchWeight = 0.0;
					for (String s : cSpecs) matchWeight += specificFreq.getOrDefault(s, 0L) * 0.5;
					for (String cat : cCats) matchWeight += categoryFreq.getOrDefault(cat, 0L) * 0.3;
					for (String dom : cDoms) matchWeight += domainFreq.getOrDefault(dom, 0L) * 0.2;

					double genreScore = Math.min(1.0, matchWeight / Math.max(1.0, userTotalWeight));
					boolean isFav = c.getCasts().stream().anyMatch(preferredIds::contains);
					int matchingRate = (int) Math.round(50 + ((genreScore * 0.5 + (isFav ? 0.5 : 0.0)) * 50));

					return RecommendResponseDto.ConcertDto.builder()
							.concertName(c.getConcertName()).matchingRate(Math.min(100, matchingRate))
							.casts(casts.stream().map(Artist::getArtistName).toList())
							.genres(cSpecs.stream().limit(3).toList()).build();
				}).sorted(Comparator.comparingInt(RecommendResponseDto.ConcertDto::getMatchingRate).reversed()).toList())
				.build();
	}

	@Transactional
	public RecommendResponseDto updatePreference(String token) {
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
		catch (HttpClientErrorException.Unauthorized e) {
			// spotify 세션 만료 or spotify 오류
			throw new BusinessException(ErrorCode.SESSION_EXPIRED);
		}
		catch (Exception e) {
			// DB 저장 오류 발생
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "데이터 동기화 중 오류가 발생했습니다.");
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
