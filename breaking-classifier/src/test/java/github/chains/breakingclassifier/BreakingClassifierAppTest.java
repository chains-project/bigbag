package github.chains.breakingclassifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BreakingClassifierAppTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldWriteJsonOutputWhenOptionProvided() throws Exception {
        Path log = Path.of("src/test/resources/logs/sample-maven-build.log");
        Path tempDir = Files.createTempDirectory("breaking-classifier");
        Path outputJson = tempDir.resolve("report.json");

        BreakingClassifierApp app = new BreakingClassifierApp(new MavenErrorExtractor(), objectMapper);
        CommandLine cmd = new CommandLine(app);

        int exitCode = cmd.execute(log.toString(), "--json-output", outputJson.toString());

        assertThat(exitCode).isZero();
        assertThat(outputJson).exists();

        BreakingReport report = objectMapper.readValue(outputJson.toFile(), BreakingReport.class);
        assertThat(report.originalFailurePath()).isEqualTo(log.toString());
        assertThat(report.failureCategory()).isEqualTo(FailureCategory.COMPILATION_FAILURE);
        assertThat(report.errorsByFile()).hasSize(4);

        FileErrorGroup sslFilterReport = report.errorsByFile().stream()
                .filter(fileErrors -> fileErrors.filePath().endsWith("SSLFilter.java"))
                .findFirst()
                .orElseThrow();

        assertThat(sslFilterReport.errors()).hasSize(2);
        assertThat(sslFilterReport.errors().getFirst().message()).contains("constructor SslFilter");
    }
}

