package github.chains.breakingclassifier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MavenErrorExtractorTest {

    private final MavenErrorExtractor extractor = new MavenErrorExtractor();

    @Test
    void shouldExtractCompilerErrorsFromLog() throws IOException {
        Path log = Path.of("src/test/resources/logs/sample-maven-build.log");

        List<BreakingError> errors = extractor.extract(log);

        assertThat(errors).hasSize(5);

        BreakingError first = errors.get(0);
        assertThat(first.filePath()).isEqualTo("/quickfixj/quickfixj-core/src/main/java/quickfix/mina/acceptor/AbstractSocketAcceptor.java");
        assertThat(first.lineNumber()).isEqualTo(136);
        assertThat(first.columnNumber()).isEqualTo(18);
        assertThat(first.message()).isEqualTo("cannot find symbol");
        assertThat(first.details()).containsExactly(
                "symbol:   method setUseClientMode(boolean)",
                "location: variable sslFilter of type quickfix.mina.ssl.SSLFilter"
        );
        assertThat(first.failureCategory()).isEqualTo(FailureCategory.COMPILATION_FAILURE);

        BreakingError third = errors.get(2);
        assertThat(third.filePath()).contains("quickfix/mina/ssl/SSLFilter.java");
        assertThat(third.details()).containsExactly(
                "required: javax.net.ssl.SSLContext",
                "found: javax.net.ssl.SSLContext,boolean",
                "reason: actual and formal argument lists differ in length"
        );
    }

    @Test
    void shouldSupportParsingFromReader() throws IOException {
        String log = """
                [INFO] --- maven-compiler-plugin:3.11.0:compile (default-compile) @ app ---
                [ERROR] COMPILATION ERROR :
                [ERROR] /project/src/Foo.java:[10,5] cannot find symbol
                [ERROR]   symbol:   class Bar
                [ERROR]   location: class Foo
                [INFO] BUILD FAILURE
                """;

        List<BreakingError> errors = extractor.extract(new java.io.BufferedReader(new StringReader(log)));

        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.filePath()).isEqualTo("/project/src/Foo.java");
            assertThat(error.lineNumber()).isEqualTo(10);
            assertThat(error.columnNumber()).isEqualTo(5);
            assertThat(error.details()).containsExactly(
                    "symbol:   class Bar",
                    "location: class Foo"
            );
            assertThat(error.failureCategory()).isEqualTo(FailureCategory.COMPILATION_FAILURE);
        });
    }

    @Test
    void shouldFailWhenLogFileDoesNotExist() {
        Path missing = Path.of("missing.log");

        assertThrows(IOException.class, () -> extractor.extract(missing));
    }
}

