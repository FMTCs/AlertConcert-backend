package fcmt.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

	// application.properties의 값을 변수에 할당
	@Value("${spring.ai.openai.api-key}")
	private String apiKey;

	@Value("${spring.ai.openai.base-url}")
	private String baseUrl;

	@Value("${spring.ai.openai.chat.completions-path}")
	private String completionsPath;

	@Value("${spring.ai.openai.chat.model}")
	private String model;

	// @Bean
	// public OpenAiApi openAiApi() {
	// return
	// OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).completionsPath(completionsPath).build();
	// }
	//
	// @Bean
	// public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
	// return OpenAiChatModel.builder()
	// .openAiApi(openAiApi)
	// .defaultOptions(OpenAiChatOptions.builder().model(model).build())
	// .build();
	// }

}
