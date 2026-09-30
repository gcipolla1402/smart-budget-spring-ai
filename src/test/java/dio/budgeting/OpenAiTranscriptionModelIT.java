package dio.budgeting;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiTranscriptionModelIT {
    @Autowired
    OpenAiAudioTranscriptionModel openAiTranscriptionModel;

    @ParameterizedTest
    @CsvSource({
            "recording-1.m4a, 80, oitenta",
            "recording-2.m4a, 40, quarenta",
            "recording-3.m4a, 120, cento e vinte",
            "recording-4.m4a, 90, noventa",
            "recording-5.m4a, 200, duzentos",
            "recording-6.m4a, 60, sessenta",
    })
    public void should_containExpectedKeywords_when_audioFilesAreProcessed(
            String fileName, String amount, String amountInWords) {
        var recording = new ClassPathResource("audio/" + fileName);

        var response = openAiTranscriptionModel.call(recording);

        var normalizedResponse = response.toLowerCase(Locale.ROOT);
        var acceptedRepresentations = List.of(
                amount + " reais",
                amountInWords + " reais",
                "r$ " + amount + ",00",
                "r$ " + amount + ".00");

        assertThat(acceptedRepresentations)
                .as("a transcrição deve conter o valor monetário falado no áudio")
                .anyMatch(normalizedResponse::contains);
        System.out.println(response);
    }
}
