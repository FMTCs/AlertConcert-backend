package fcmt.backend.ai;

import fcmt.backend.dto.ArtistAiResponseDto;
import fcmt.backend.dto.ArtistListWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

	private final ChatModel chatModel;

	// 1단계: 공연 정보를 바탕으로 아티스트 이름 리스트 추출
	// TODO: 포스터는 이미지가지고 넣어서 뭐 어찌저찌 하자고 했던거같은데.. 일단 그건 나중에..
	// TODO: Media 객체를 활용해 멀티모달 프롬프트를 구성해야..한다고 함
	public List<String> fetchArtistList(String concertName, String posterUrl) {
		ListOutputConverter outputConverter = new ListOutputConverter(new DefaultConversionService());
		String format = outputConverter.getFormat();

		String template = """
				콘서트명: {concertName}
				포스터 URL: {posterUrl}
				위 정보를 바탕으로 이 공연에 출연하는 주요 가수(가창자)들의 실제 이름을 리스트로 알려줘.
				{format}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(template);
		Prompt prompt = promptTemplate
			.create(Map.of("concertName", concertName, "posterUrl", posterUrl, "format", format));

		try {
			String response = chatModel.call(prompt).getResult().getOutput().getText();
			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("가수 리스트 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 2단계: 아티스트 이름을 기반으로 Spotify ID 추출
	public ArtistIdRecord fetchSpotifyIdByArtistName(String artistName) {
		BeanOutputConverter<ArtistIdRecord> outputConverter = new BeanOutputConverter<>(ArtistIdRecord.class);
		String format = outputConverter.getFormat();

		String template = """
				아티스트 '{artistName}'의 공식 Spotify Artist ID를 찾아줘.
				{format}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(template);
		Prompt prompt = promptTemplate.create(Map.of("artistName", artistName, "format", format));

		try {
			String response = chatModel.call(prompt).getResult().getOutput().getText();
			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("Spotify ID 추출 실패 ({}): {}", artistName, e.getMessage());
			return null;
		}
	}

	// 결과 매핑을 위한 Record 정의
	public record ArtistIdRecord(String name, String spotify_id) {
	}

	@Value("classpath:genres.json") // 리소스 폴더의 genres.json 로드
	private Resource genresResource;

	public List<ArtistAiResponseDto> fetchGenresFromAi(List<Map<String, String>> artists) {
		// BeanOutputConverter를 사용하여 JSON 응답을 DTO 리스트로 자동 변환
		// List를 직접 반환받기 위해 ListOutputConverter 대신 익명 클래스 혹은 Wrapper 활용 권장
		// 여기서는 가장 안정적인 ArtistAiResponseDto 배열/리스트 변환 방식을 사용합니다.
		BeanOutputConverter<ArtistListWrapper> outputConverter = new BeanOutputConverter<>(ArtistListWrapper.class);
		String format = outputConverter.getFormat();
		try {
			// 1. genres.json 파일 읽기
			String allowedGenres = Files.readString(Path.of(genresResource.getURI()));

			// 2. 아티스트 정보를 프롬프트용 문자열로 변환
			String artistListStr = artists.stream()
				.map(m -> String.format("- Name: %s (ID: %s)", m.get("artist_name"), m.get("spotify_artist_id")))
				.collect(Collectors.joining("\n"));

			String template = """
					You are a music genre classifier.
					I will provide a list of artists. You must return their genres ONLY from the provided allowed genres list.

					Artists:
					{artistListStr}

					Allowed Genres:
					{allowedGenres}

					{format}
					""";

			PromptTemplate promptTemplate = new PromptTemplate(template);
			Prompt prompt = promptTemplate
				.create(Map.of("artistListStr", artistListStr, "allowedGenres", allowedGenres, "format", format));

			// 5. 응답 결과 파싱 (간략화된 예시)
			// 실제로는 ApiResponse 객체를 정의하여 받아오는 것이 좋습니다.
			String response = chatModel.call(prompt).getResult().getOutput().getText();
			return outputConverter.convert(response).artists();

		}
		catch (Exception e) {
			log.error("AI 장르 추출 중 오류 발생: ", e);
			return List.of();
		}
	}

}