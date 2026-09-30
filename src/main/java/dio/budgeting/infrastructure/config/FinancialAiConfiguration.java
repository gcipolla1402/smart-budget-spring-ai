package dio.budgeting.infrastructure.config;

import dio.budgeting.application.CalculateCategoryTotalUseCase;
import dio.budgeting.application.GenerateFinancialSummaryUseCase;
import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.infrastructure.ai.FinancialAiService;
import dio.budgeting.infrastructure.ai.FinancialQueryTools;
import dio.budgeting.infrastructure.ai.OpenAiFinancialAiService;
import dio.budgeting.infrastructure.ai.UnavailableFinancialAiService;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
public class FinancialAiConfiguration {

    @Bean
    FinancialAiService financialAiService(ObjectProvider<TranscriptionModel> transcriptionModels,
                                          ObjectProvider<ChatClient.Builder> chatClientBuilders,
                                          ObjectProvider<TextToSpeechModel> textToSpeechModels,
                                          @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                          PersistTransactionUseCase persistTransactionUseCase,
                                          ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                          CalculateCategoryTotalUseCase calculateCategoryTotalUseCase,
                                          GenerateFinancialSummaryUseCase generateFinancialSummaryUseCase,
                                          FinancialQueryTools financialQueryTools,
                                          Clock clock) throws IOException {
        var transcriptionModel = transcriptionModels.getIfAvailable();
        var chatClientBuilder = chatClientBuilders.getIfAvailable();
        var textToSpeechModel = textToSpeechModels.getIfAvailable();

        if (transcriptionModel == null || chatClientBuilder == null) {
            return new UnavailableFinancialAiService();
        }

        var chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .build();

        return new OpenAiFinancialAiService(
                transcriptionModel, chatClient, textToSpeechModel, clock, persistTransactionUseCase,
                listTransactionsByCategoryUseCase, calculateCategoryTotalUseCase,
                generateFinancialSummaryUseCase, financialQueryTools);
    }
}
