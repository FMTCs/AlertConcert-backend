package fcmt.backend.controller;
// TODO: 테스트를 위해 ConcertTestingController를 생성했음. 삭제 필요.

import fcmt.backend.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class ConcertTestingController {

    private final ConcertService concertService;

    @GetMapping("/run-kopis")
    public String runKopis() {
        try {
            // 4시까지 안 기다리고 바로 수집 로직 실행
            concertService.syncKopisData();
            return "[O] KOPIS 데이터 수집 요청 성공! 서버 로그를 확인하세요.";
        } catch (Exception e) {
            e.printStackTrace();
            return "[X] 수집 실패: " + e.getMessage();
        }
    }
}