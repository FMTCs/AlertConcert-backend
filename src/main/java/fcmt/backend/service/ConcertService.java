package fcmt.backend.service;

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

		log.info(">>> 일일 작업 완료");
	}

	public void syncKopisData() {
		// 날짜 계산: 오늘 ~ 1년
		LocalDate now = LocalDate.now();
		LocalDate oneYearLater = now.plusYears(1);

		// KOPIS 형식(yyyyMMdd)으로
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		String stdate = now.format(formatter);
		String eddate = oneYearLater.format(formatter);

		// TODO: 몇 개의 공연 정보를 들고 올 지 결정 필요.
		int cpage = 1;
		int rows = 100;
		boolean hasMoreData = true;

		log.info("수집 기간: {} ~ {}", stdate, eddate);

		while (hasMoreData) {
			String listUrl = String.format(
					"http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=%d&rows=%d",
					serviceKey, stdate, eddate, cpage, rows);

			KopisListResponse listResponse = restTemplate.getForObject(listUrl, KopisListResponse.class);

			if (listResponse != null && listResponse.getConcertList() != null) {
				for (KopisListResponse.KopisListDto listDto : listResponse.getConcertList()) {
					try {
						fetchAndSaveDetail(listDto.getMt20id());
						// 0.2초 대기 (1초에 최대 약 5번 요청하게 됨)
						Thread.sleep(200);

					}
					catch (InterruptedException e) {
						log.error("작업 중 인터럽트 발생: {}", e.getMessage());
						Thread.currentThread().interrupt(); // 상태 복구
						break;
					}
					catch (Exception e) {
						log.error("상세 정보 저장 실패 (ID: {}): {}", listDto.getMt20id(), e.getMessage());
					}
				}
				cpage++;
				// TODO: 테스트를 위해서 300개로 제한. 실제로 돌릴 땐 제거하면 됨.
				if (cpage > 3)
					break;
			}
			else {
				// 더 이상 가져올 데이터가 없으면 루프 종료
				log.info("모든 데이터 수집 완료. 마지막 페이지: {}", cpage - 1);
				hasMoreData = false;
			}
			// API 서버 부하 방지를 위해 페이지 전환 사이에도 잠깐 쉬어주기
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ignored) {
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

		// Genre 리스트 생성
		List<String> genreList = new ArrayList<>(List.of(dto.getGenrenm().split(", ")));

		if (existingConcert.isPresent()) {
			Concert concert = existingConcert.get();
			// 변경을 감지하고, 주요 정보가 전날과 다를 때만 업데이트 수행
			if (isDataNotChanged(concert, dto, startDate, endDate, currentBookingUrl)) {
				return;
			}

			concert.setGenres(genreList);
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
				.genres(genreList)
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