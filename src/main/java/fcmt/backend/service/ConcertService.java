package fcmt.backend.service;

import fcmt.backend.ai.AiClient;
import fcmt.backend.entity.Artist;
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

	private final RestTemplate restTemplate = new RestTemplate();

	@Value("${kopis.api.key}") // TODO: 지금은 application.properties에 저장하긴 했는데.. 이거 어떻게 관리?
	private String serviceKey;

	@Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시에 실행 (초 분 시 일 월 요일)
	public void dailyJob() {
		log.info(">>> 매일 데이터 수집 및 AI 업데이트 시작: {}", LocalDate.now());

		// 1. KOPIS 데이터 동기화
		syncKopisData();

		// 2. AI 출연진 정보 업데이트 (이후에 구현할 메서드)
		// updateCastsWithAI();
		// List<Map<String, String>> spotifyArtistIds = new ArrayList<>();
		// 3, 해당 출연진 정보를 받아서 장르 추출 및 artists 테이블 채우기
		// List<Long> artistIds = artistService.addAndGetArtistIds(spotifyArtistIds);
		// log.info(">>> 해당 출연진의 id 총 개수는 {}입니다.", artistIds.size());
	}

	//
	// 1. KOPIS 데이터 동기화
	//
	public void syncKopisData() {
		LocalDate now = LocalDate.now();
		LocalDate oneYearLater = now.plusYears(1);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		String stdate = now.format(formatter);
		String eddate = oneYearLater.format(formatter);

		// 1. 대상 장르 코드 리스트 정의 (CCCD: 대중음악 -> CCCA: 서양음악(클래식) -> CCCC: 국악)
		List<String> genreCodes = List.of("CCCD", "CCCA", "CCCC"); // GGGA: 뮤지컬 필요한가?

		log.info("수집 기간: {} ~ {}", stdate, eddate);

		for (String genreCode : genreCodes) {
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
						try {
							fetchAndSaveDetail(listDto.getMt20id());
							Thread.sleep(300);
						}
						catch (InterruptedException e) {
							log.error("작업 중 인터럽트 발생: {}", e.getMessage());
							Thread.currentThread().interrupt();
							return; // 전체 종료
						}
						catch (Exception e) {
							log.error("상세 정보 저장 실패 (ID: {}): {}", listDto.getMt20id(), e.getMessage());
						}
					}
					cpage++;

					// 테스트용 제한
					if (cpage > 3)
						break;
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
	}

	private void fetchAndSaveDetail(String mt20id) {
		String detailUrl = "http://www.kopis.or.kr/openApi/restful/pblprfr/" + mt20id + "?service=" + serviceKey;
		KopisDetailResponse detailResponse = restTemplate.getForObject(detailUrl, KopisDetailResponse.class);

		if (detailResponse != null && detailResponse.getDetail() != null) {
			saveOrUpdateConcert(detailResponse.getDetail());
		}
	}

	private void saveOrUpdateConcert(KopisDetailResponse.KopisDetailDto dto) {
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
				return;
			}

			concert.setPosterImgUrl(dto.getPoster());
			concert.setBookingUrl(currentBookingUrl);
			concert.setPerformanceStartDate(startDate);
			concert.setPerformanceEndDate(endDate);

			concertRepository.save(concert);
			log.info("업데이트 완료: {}", dto.getPrfnm());
		}
		else {
			// 새로 생성
			Concert newConcert = Concert.builder()
				.concertName(dto.getPrfnm())
				.posterImgUrl(dto.getPoster())
				.performanceStartDate(LocalDate.parse(dto.getPrfpdfrom().replace(".", "-")))
				.performanceEndDate(LocalDate.parse(dto.getPrfpdto().replace(".", "-")))
				.bookingUrl(dto.getRelates() != null ? dto.getRelates().getFirstUrl() : null)
				.casts(new ArrayList<>())
				.build();
			concertRepository.save(newConcert);
			log.info("신규 저장 완료: {}", dto.getPrfnm());
		}
	}

	//
	// 2. AI 출연진 정보 업데이트
	//
	private void updateCastsWithAI() {
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

}