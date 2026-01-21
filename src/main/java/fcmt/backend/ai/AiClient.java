package fcmt.backend.ai;

import fcmt.backend.dto.ArtistAiResponseDto;
import fcmt.backend.dto.ArtistListWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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

	// [Global System Prompt]
	private static final String SYSTEM_PROMPT_TEXT = """
			당신은 한국에서 열리는 음악 콘서트를 대상으로 아티스트 데이터를 추출하는 AI 에이전트입니다.

			[글로벌 컨텍스트]
			- 도메인: 한국에서 열리는 대중음악 콘서트 (K-POP, 인디, 락 등).

			[절대 규칙]
			1. 출력 형식: 오직 JSON만 출력하세요. 마크다운(```json)이나 부가 설명은 절대 금지입니다.
			2. 인용 금지: [1], [2] 와 같은 인용 표기를 포함하지 마세요.
			3. 그룹 처리: 밴드나 그룹은 단일 엔티티로 처리하세요. 멤버 개개인을 나열하지 마세요.
			4. 환각 방지: 데이터가 확실하지 않으면 빈 문자열("")을 반환하세요. ID를 임의로 지어내지 마세요.
			""";

	// 1단계: 공연 정보를 바탕으로 아티스트 이름 리스트 추출
	public List<String> fetchArtistList(String concertName, String posterUrl) {
		ListOutputConverter outputConverter = new ListOutputConverter(new DefaultConversionService());
		String format = outputConverter.getFormat();

		SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT_TEXT);

		String userTemplate = """
				[입력 데이터]
				콘서트명: {concertName}
				포스터 URL: {posterUrl}

				[임무]
				입력된 정보에서 공연의 '메인 아티스트(가수/그룹)' 이름을 리스트로 추출하세요.
				반드시 밴드나 그룹은 단일 엔티티로 처리하세요. 멤버 개개인을 나열하지 마세요.
				그룹의 일원의 경우, "가수명 Spotify"로 검색 시, spotify 페이지에 그룹명이 먼저 출력된다는 점을 이용하세요.

				[응답 형식]
				{format}""";

		PromptTemplate promptTemplate = new PromptTemplate(userTemplate);
		UserMessage userMessage = new UserMessage(
				promptTemplate.render(Map.of("concertName", concertName, "posterUrl", posterUrl, "format", format)));

		try {
			String response = chatModel.call(new Prompt(List.of(systemMessage, userMessage)))
				.getResult()
				.getOutput()
				.getText();
			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("가수 리스트 추출 실패: {}", e.getMessage());
			return List.of();
		}
	}

	// 2단계: 아티스트 이름을 기반으로 Spotify ID 추출
	// TODO: 오류로 인해 Spotify id를 추출하지 못한 경우, 이후 다시 순회하면서 찾을 수 있도록 해야함.
	public List<ArtistIdRecord> fetchSpotifyIdByArtistName(String concertName, List<String> artistNames) {
		BeanOutputConverter<List<ArtistIdRecord>> outputConverter = new BeanOutputConverter<>(
				new org.springframework.core.ParameterizedTypeReference<>() {
				});
		String format = outputConverter.getFormat();

		// 리스트 포매팅 (1. 이름 \n 2. 이름)
		String artistListStr = java.util.stream.IntStream.range(0, artistNames.size())
			.mapToObj(i -> String.format("%d. %s", i + 1, artistNames.get(i)))
			.collect(Collectors.joining("\n"));

		SystemMessage systemMessage = new SystemMessage(SYSTEM_PROMPT_TEXT);

		String userTemplate = """
				[입력 데이터]
				콘서트명: {concertName}
				대상 아티스트:
				{artistListStr}

				[임무]
				위 아티스트들의 '공식 영문명(Official English Name)'과 'Spotify Artist ID'를 찾아주세요.

				[검색 및 추출 전략]
				1. 검색 방법: 입력된 이름 그대로 "가수명 Spotify"로 검색하세요.
				2. 동명이인 구별: 검색 결과가 여러 개일 경우, 콘서트명의 장르나 성격, 그리고 '한국 활동 여부'를 고려하여 가장 적합한 아티스트 1명을 선정하세요.
				3. ID 추출 패턴:
				   - 검색된 URL은 보통 `http://.../artist/ARTIST_ID` 형태입니다.
				   - `/artist/` 바로 뒤에 오는 `ARTIST_ID` 부분만 추출하세요.
				4. 예외 처리: 확실한 검색 결과가 없다면 spotify_id를 빈 값("")으로 두세요.

				[응답 형식]
				{format}
				""";

		PromptTemplate promptTemplate = new PromptTemplate(userTemplate);
		UserMessage userMessage = new UserMessage(promptTemplate
			.render(Map.of("concertName", concertName, "artistListStr", artistListStr, "format", format)));

		try {
			String response = chatModel.call(new Prompt(List.of(systemMessage, userMessage)))
				.getResult()
				.getOutput()
				.getText();
			log.info("AI Raw Response: {}", response);

			int start = response.indexOf("[");
			int end = response.lastIndexOf("]");
			if (start != -1 && end != -1) {
				response = response.substring(start, end + 1);
			}

			return outputConverter.convert(response);
		}
		catch (Exception e) {
			log.error("Spotify ID 추출 실패: {}", e.getMessage());
			return List.of();
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