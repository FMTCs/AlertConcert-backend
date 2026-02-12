package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.entity.Concert;
import fcmt.backend.repository.ConcertRepository;
import fcmt.backend.dto.KopisListResponse;
import fcmt.backend.dto.KopisDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // for logging
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertService {

	private final ConcertRepository concertRepository;

	// private final ArtistService artistService;

	private final AiClient aiClient;

	private final SpotifySearchService spotifySearchService;

	private final RestTemplate restTemplate = new RestTemplate();

	private final ConcertCastApplyService concertCastApplyService;

	@Value("${kopis.api.key}") // TODO: 지금은 application.properties에 저장하긴 했는데.. 이거 어떻게 관리?
	private String serviceKey;

	@Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시에 실행 (초 분 시 일 월 요일)
	public void dailyJob() {
		log.info(">>> 매일 데이터 수집 및 AI 업데이트 시작: {}", LocalDate.now());

		// 1. KOPIS 데이터 동기화 (변경/신규 concert_id 목록을 리턴하도록 바꾼 버전 기준)
		List<Long> changedConcertIds = syncKopisData();

		// 2. AI + Spotify로 출연진 정보 "추출"
		var extracted = extractArtistsInfosWithAI(changedConcertIds);

		log.info(">>> 추출 완료. 대상 콘서트={}, 성공 결과={}", changedConcertIds.size(), extracted.size());

		// 3. extracted 기반으로 artists 테이블 upsert + concerts.casts 업데이트
		concertCastApplyService.applyExtracted(extracted);
	}

	//
	// 1. KOPIS 데이터 동기화
	//
	public List<Long> syncKopisData() {
		LocalDate now = LocalDate.now();
		LocalDate oneYearLater = now.plusYears(1);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		String stdate = now.format(formatter);
		String eddate = oneYearLater.format(formatter);

		// 1. 대상 장르 코드 리스트 정의 (CCCD: 대중음악 -> CCCA: 서양음악(클래식))
		List<String> genreCodes = List.of("CCCD", "CCCA");

		log.info("수집 기간: {} ~ {}", stdate, eddate);

		// return할 변경된 ConcertId 목록 (중복 방지, 순서 유지를 위해 Set 사용)
		java.util.Set<Long> changedConcertIdSet = new java.util.LinkedHashSet<>();

		// TODO: need to remove processedDetails, outer 관련 내용들 (테스트용임)
		int processedDetails = 0;
		outer: for (String genreCode : genreCodes) {
			log.info("장르 코드 [{}] 수집 시작", genreCode);
			int cpage = 1;
			int rows = 100;
			boolean hasMoreData = true;

			while (hasMoreData) {
				// 2. URL에 shcate 파라미터 추가
				String listUrl = String.format(
						"http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d&shcate=%s",
						serviceKey, stdate, eddate, cpage, rows, genreCode);

				KopisListResponse listResponse = restTemplate.getForObject(listUrl, KopisListResponse.class);

				if (listResponse != null && listResponse.getConcertList() != null) {
					for (KopisListResponse.KopisListDto listDto : listResponse.getConcertList()) {
						if (processedDetails >= 10) { // TODO: need to remove
							break outer;
						}
						try {
							Optional<Long> changedId = fetchAndSaveDetail(listDto.getMt20id());
							changedId.ifPresent(changedConcertIdSet::add);
							Thread.sleep(300);
							processedDetails++; // TODO: need to remove
						}
						catch (InterruptedException e) {
							log.error("작업 중 인터럽트 발생: {}", e.getMessage());
							Thread.currentThread().interrupt();
							return new ArrayList<>(changedConcertIdSet);
						}
						catch (Exception e) {
							log.error("상세 정보 저장 실패 (ID: {}): {}", listDto.getMt20id(), e.getMessage());
						}
					}
					cpage++;
				}
				else {
					log.info("장르 [{}] 수집 완료. 마지막 페이지: {}", genreCode, cpage - 1);
					hasMoreData = false;
				}

				try {
					// API 서버 부하 방지를 위해 페이지 전환 사이에도 잠깐 쉬어주기

					Thread.sleep(500);
				}
				catch (InterruptedException ignored) {
				}
			}
		}
		return new ArrayList<>(changedConcertIdSet);
	}

	private Optional<Long> fetchAndSaveDetail(String mt20id) {
		String detailUrl = "http://www.kopis.or.kr/openApi/restful/pblprfr/" + mt20id + "?service=" + serviceKey;
		KopisDetailResponse detailResponse = restTemplate.getForObject(detailUrl, KopisDetailResponse.class);

		if (detailResponse != null && detailResponse.getDetail() != null) {
			return saveOrUpdateConcert(detailResponse.getDetail());
		}
		return Optional.empty();
	}

	private Optional<Long> saveOrUpdateConcert(KopisDetailResponse.KopisDetailDto dto) {
		// 출연진(Cast) 정보는 항상 비워두는 것으로 수정함. 무조건 ai로 채우게 해서 데이터의 일관성 유지
		// 공연명 기준으로 중복 체크
		Optional<Concert> existingConcert = concertRepository.findByConcertName(dto.getPrfnm());

		// KOPIS 날짜 포맷 변환 (yyyy.MM.dd -> yyyy-MM-dd)
		LocalDate startDate = LocalDate.parse(dto.getPrfpdfrom().replace(".", "-"));
		LocalDate endDate = LocalDate.parse(dto.getPrfpdto().replace(".", "-"));
		String currentBookingUrl = dto.getRelates() != null ? dto.getRelates().getFirstUrl() : null;

		if (existingConcert.isPresent()) {
			Concert concert = existingConcert.get();
			// 변경을 감지하고, 주요 정보가 전날과 다를 때만 업데이트 수행
			if (isDataNotChanged(concert, dto, startDate, endDate, currentBookingUrl)) {
				return Optional.empty();
			}

			concert.setPosterImgUrl(dto.getPoster());
			concert.setBookingUrl(currentBookingUrl);
			concert.setPerformanceStartDate(startDate);
			concert.setPerformanceEndDate(endDate);

			Concert saved = concertRepository.save(concert);
			log.info("업데이트 완료: {}", dto.getPrfnm());

			return Optional.of(saved.getConcertId());
		}
		else {
			// 새로 생성
			Concert newConcert = Concert.builder()
				.concertName(dto.getPrfnm())
				.posterImgUrl(dto.getPoster())
				.performanceStartDate(startDate)
				.performanceEndDate(endDate)
				.bookingUrl(currentBookingUrl)
				.casts(new ArrayList<>()) // casts는 AI로 채울 거라 비움
				.build();

			Concert saved = concertRepository.save(newConcert);
			log.info("신규 저장 완료: {}", dto.getPrfnm());

			return Optional.of(saved.getConcertId());
		}
	}

	// 데이터 변경이 있는지 감지
	private boolean isDataNotChanged(Concert concert, KopisDetailResponse.KopisDetailDto dto, LocalDate startDate,
			LocalDate endDate, String bookingUrl) {
		return Objects.equals(concert.getConcertName(), dto.getPrfnm())
				&& Objects.equals(concert.getPosterImgUrl(), dto.getPoster())
				&& Objects.equals(concert.getPerformanceStartDate(), startDate)
				&& Objects.equals(concert.getPerformanceEndDate(), endDate)
				&& Objects.equals(concert.getBookingUrl(), bookingUrl);
	}

	//
	// 2. AI + Spotify로 출연진 정보 "추출"
	//
	// TODO: public -> private으로 다시 수정해야함(테스트를 위해 public으로 변경해둠)
	public static record ConcertArtistExtractResult(Long concertId, String concertName, List<String> artistNames,
			List<AiClient.ArtistIdRecord> spotifyDetails) {
	}

	// TODO: public -> private으로 다시 수정해야함(테스트를 위해 public으로 변경해둠)
	public List<ConcertArtistExtractResult> extractArtistsInfosWithAI(List<Long> changedConcertIds) {
		if (changedConcertIds == null || changedConcertIds.isEmpty()) {
			log.info(">>> 추출 대상 콘서트 없음 (변경/신규 없음)");
			return List.of();
		}
		List<ConcertArtistExtractResult> results = new ArrayList<>();

		// concert_id 기준으로 다시 DB에서 꺼내기
		List<Concert> targets = concertRepository.findAllById(changedConcertIds);

		for (Concert concert : targets) {
			Long concertId = concert.getConcertId();
			String concertName = concert.getConcertName();

			try {
				// 1) AI로 아티스트명 리스트 추출
				List<String> artistNames = aiClient.fetchArtistList(concertName)
					.stream()
					.filter(a -> a != null && !a.isBlank())
					.distinct()
					.toList();

				// 2) Spotify Search로 (name, spotify_artist_id) 추출
				List<AiClient.ArtistIdRecord> spotifyDetails = artistNames.stream()
					.map(name -> spotifySearchService.searchArtist(name).orElse(null))
					.filter(Objects::nonNull)
					.toList();
				// TODO: need to remove 로그들
				log.info(">>> [AI+Spotify] concert_id={}, name='{}'", concertId, concertName);
				log.info("    - artistNames({}): {}", artistNames.size(), artistNames);
				log.info("    - spotifyDetails({}): {}", spotifyDetails.size(), spotifyDetails);

				var hitNames = spotifyDetails.stream()
					.map(AiClient.ArtistIdRecord::name)
					.collect(java.util.stream.Collectors.toSet());

				var misses = artistNames.stream().filter(a -> !hitNames.contains(a)).toList();

				if (!misses.isEmpty()) {
					log.warn("    - spotifyMisses({}): {}", misses.size(), misses);
				} // TODO: need to remove 여기까지

				results.add(new ConcertArtistExtractResult(concertId, concertName, artistNames, spotifyDetails));
			}
			catch (Exception e) {
				log.warn(">>> extract failed: concert_id={}, name='{}', err={}", concertId, concertName,
						e.getMessage());
			}
		}
		return results;
	}

}