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

		log.info("수집 기간: {} ~ {}", stdate, eddate);

		String listUrl = String.format(
				"http://www.kopis.or.kr/openApi/restful/pblprfr?service=%s&stdate=%s&eddate=%s&cpage=1&rows=100",
				serviceKey, stdate, eddate);

		KopisListResponse listResponse = restTemplate.getForObject(listUrl, KopisListResponse.class);

		if (listResponse != null && listResponse.getConcertList() != null) {
			for (KopisListResponse.KopisListDto listDto : listResponse.getConcertList()) {
				fetchAndSaveDetail(listDto.getMt20id());
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
		// 출연진(Cast) 정보 있으면 저장하고, 없으면 null로 저장
		String rawCast = dto.getPrfcast();
		String castJson = null;
		if (rawCast != null && !rawCast.isBlank() && !rawCast.equals("-")) {
			castJson = String.format("{\"rawCast\": \"%s\"}", rawCast);
		}

		// 공연명 기준으로 중복 체크
		Optional<Concert> existingConcert = concertRepository.findByConcertName(dto.getPrfnm());

		if (existingConcert.isPresent()) { // TODO: updatedate 최종수정일 이용해서 업데이트 여부 결정하는 게 더
											// 좋을지도..? 좀 귀찮넹 일단 스킵
			// 이미 있다면 정보 업데이트 (기존 ID 유지)
			Concert concert = existingConcert.get();
			concert.setGenres(dto.getGenrenm().split(", "));
			concert.setPosterImgUrl(dto.getPoster());
			concert.setBookingUrl(dto.getRelates() != null ? dto.getRelates().getFirstUrl() : null);
			// 만약 기존에 casts 정보가 없었는데 이번에 들어왔다면 업데이트
			if (concert.getCasts() == null) {
				concert.setCasts(castJson);
			}
			concertRepository.save(concert);
			log.info("업데이트 완료: {}", dto.getPrfnm());
		}
		else {
			// 새로 생성
			Concert newConcert = Concert.builder()
				.concertName(dto.getPrfnm())
				.genres(dto.getGenrenm().split(", "))
				.posterImgUrl(dto.getPoster())
				.bookingStartDate(LocalDate.parse(dto.getPrfpdfrom().replace(".", "-")))
				.bookingEndDate(LocalDate.parse(dto.getPrfpdto().replace(".", "-")))
				.bookingUrl(dto.getRelates() != null ? dto.getRelates().getFirstUrl() : null)
				.casts(castJson)
				.build();
			concertRepository.save(newConcert);
			log.info("신규 저장 완료: {}", dto.getPrfnm());
		}
	}

}