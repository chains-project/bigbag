package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path inputRoot = Paths.get(args[0]);
        if (!Files.isDirectory(inputRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + inputRoot);
        }

        Path outputRoot = resolveJavaSourceRoot(inputRoot);

        if (!usesLogbackClassicLogger(inputRoot)) {
            return;
        }

        try {
            createCompatibilityType(outputRoot,
                    "org.slf4j.spi.LoggingEventAware",
                    "package org.slf4j.spi;\n\npublic interface LoggingEventAware {\n    void log(org.slf4j.event.LoggingEvent slf4jEvent);\n}\n");

            createCompatibilityType(outputRoot,
                    "org.slf4j.impl.StaticLoggerBinder",
                    "package org.slf4j.impl;\n\nimport ch.qos.logback.classic.LoggerContext;\nimport org.slf4j.ILoggerFactory;\n\npublic final class StaticLoggerBinder {\n    public static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();\n    public static final String REQUESTED_API_VERSION = \"1.7.36\";\n\n    private final LoggerContext loggerContext = new LoggerContext();\n\n    private StaticLoggerBinder() {\n        loggerContext.start();\n    }\n\n    public static StaticLoggerBinder getSingleton() {\n        return SINGLETON;\n    }\n\n    public ILoggerFactory getLoggerFactory() {\n        return loggerContext;\n    }\n\n    public String getLoggerFactoryClassStr() {\n        return LoggerContext.class.getName();\n    }\n}\n");

            createCompatibilityType(outputRoot,
                    "org.slf4j.impl.StaticMDCBinder",
                    "package org.slf4j.impl;\n\nimport org.slf4j.helpers.BasicMDCAdapter;\nimport org.slf4j.spi.MDCAdapter;\n\npublic final class StaticMDCBinder {\n    public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();\n\n    private final MDCAdapter mdcAdapter = new BasicMDCAdapter();\n\n    private StaticMDCBinder() {\n    }\n\n    public static StaticMDCBinder getSingleton() {\n        return SINGLETON;\n    }\n\n    public MDCAdapter getMDCA() {\n        return mdcAdapter;\n    }\n\n    public String getMDCAdapterClassStr() {\n        return BasicMDCAdapter.class.getName();\n    }\n}\n");

            createCompatibilityType(outputRoot,
                    "org.slf4j.impl.StaticMarkerBinder",
                    "package org.slf4j.impl;\n\nimport org.slf4j.IMarkerFactory;\nimport org.slf4j.helpers.BasicMarkerFactory;\n\npublic final class StaticMarkerBinder {\n    public static final StaticMarkerBinder SINGLETON = new StaticMarkerBinder();\n\n    private final IMarkerFactory markerFactory = new BasicMarkerFactory();\n\n    private StaticMarkerBinder() {\n    }\n\n    public static StaticMarkerBinder getSingleton() {\n        return SINGLETON;\n    }\n\n    public IMarkerFactory getMarkerFactory() {\n        return markerFactory;\n    }\n\n    public String getMarkerFactoryClassStr() {\n        return BasicMarkerFactory.class.getName();\n    }\n}\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write compatibility shims", e);
        }
    }

    private static Path resolveJavaSourceRoot(Path inputRoot) {
        if (Files.isDirectory(inputRoot)) {
            if (inputRoot.endsWith("src/main/java") || inputRoot.endsWith("src/test/java")) {
                return inputRoot;
            }

            Path mainJava = inputRoot.resolve("src/main/java");
            if (Files.isDirectory(mainJava)) {
                return mainJava;
            }

            Path testJava = inputRoot.resolve("src/test/java");
            if (Files.isDirectory(testJava)) {
                return testJava;
            }
        }

        return inputRoot;
    }

    private static boolean usesLogbackClassicLogger(Path sourceRoot) {
        try {
            List<Path> javaFiles = Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .toList();

            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);
                if (content.contains("ch.qos.logback.classic.Logger")
                        || content.contains("ch.qos.logback.classic.LoggerContext")
                        || content.contains("ch.qos.logback.classic.")) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan sources", e);
        }
    }

    private static void createCompatibilityType(Path sourceRoot, String fqcn, String source) throws IOException {
        Path file = sourceRoot.resolve(fqcn.replace('.', '/') + ".java");
        if (Files.exists(file)) {
            return;
        }

        Files.createDirectories(file.getParent());
        CompilationUnit cu = StaticJavaParser.parse(source);
        Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
    }
}
