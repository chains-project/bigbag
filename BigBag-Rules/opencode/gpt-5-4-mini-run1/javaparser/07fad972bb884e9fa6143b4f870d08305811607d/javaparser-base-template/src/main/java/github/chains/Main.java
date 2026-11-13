package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {
        Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path sourceRoot = Files.isDirectory(projectRoot.resolve("src/main/java"))
                ? projectRoot.resolve("src/main/java")
                : projectRoot;

        ensureFile(sourceRoot, "org.slf4j.spi.LoggingEventAware",
                "package org.slf4j.spi;\n\n" +
                        "public interface LoggingEventAware {\n" +
                        "    void log(org.slf4j.event.LoggingEvent event);\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.spi.LoggingEventBuilder",
                "package org.slf4j.spi;\n\n" +
                        "public interface LoggingEventBuilder {\n" +
                        "    default LoggingEventBuilder setCause(Throwable cause) { return this; }\n" +
                        "    default LoggingEventBuilder addArgument(Object argument) { return this; }\n" +
                        "    default LoggingEventBuilder addKeyValue(String key, Object value) { return this; }\n" +
                        "    default LoggingEventBuilder addMarker(org.slf4j.Marker marker) { return this; }\n" +
                        "    default LoggingEventBuilder setMessage(String message) { return this; }\n" +
                        "    default void log() {}\n" +
                        "    default void log(String message) {}\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.event.KeyValuePair",
                "package org.slf4j.event;\n\n" +
                        "public final class KeyValuePair {\n" +
                        "    public final String key;\n" +
                        "    public final Object value;\n" +
                        "\n" +
                        "    public KeyValuePair(String key, Object value) {\n" +
                        "        this.key = key;\n" +
                        "        this.value = value;\n" +
                        "    }\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.impl.StaticLoggerBinder",
                "package org.slf4j.impl;\n\n" +
                        "public final class StaticLoggerBinder {\n" +
                        "    public static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();\n" +
                        "    private static final String LOGGER_FACTORY_CLASS_STR = ch.qos.logback.classic.LoggerContext.class.getName();\n" +
                        "    private final org.slf4j.ILoggerFactory loggerFactory = new ch.qos.logback.classic.LoggerContext();\n" +
                        "\n" +
                        "    private StaticLoggerBinder() {}\n" +
                        "\n" +
                        "    public static StaticLoggerBinder getSingleton() {\n" +
                        "        return SINGLETON;\n" +
                        "    }\n" +
                        "\n" +
                        "    public org.slf4j.ILoggerFactory getLoggerFactory() {\n" +
                        "        return loggerFactory;\n" +
                        "    }\n" +
                        "\n" +
                        "    public String getLoggerFactoryClassStr() {\n" +
                        "        return LOGGER_FACTORY_CLASS_STR;\n" +
                        "    }\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.impl.StaticMDCBinder",
                "package org.slf4j.impl;\n\n" +
                        "public final class StaticMDCBinder {\n" +
                        "    public static final StaticMDCBinder SINGLETON = new StaticMDCBinder();\n" +
                        "    private final org.slf4j.spi.MDCAdapter mdcAdapter = new ch.qos.logback.classic.util.LogbackMDCAdapter();\n" +
                        "\n" +
                        "    private StaticMDCBinder() {}\n" +
                        "\n" +
                        "    public static StaticMDCBinder getSingleton() {\n" +
                        "        return SINGLETON;\n" +
                        "    }\n" +
                        "\n" +
                        "    public org.slf4j.spi.MDCAdapter getMDCA() {\n" +
                        "        return mdcAdapter;\n" +
                        "    }\n" +
                        "\n" +
                        "    public String getMDCAdapterClassStr() {\n" +
                        "        return mdcAdapter.getClass().getName();\n" +
                        "    }\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.impl.StaticMarkerBinder",
                "package org.slf4j.impl;\n\n" +
                        "public final class StaticMarkerBinder {\n" +
                        "    public static final StaticMarkerBinder SINGLETON = new StaticMarkerBinder();\n" +
                        "    private final org.slf4j.IMarkerFactory markerFactory = org.slf4j.MarkerFactory.getIMarkerFactory();\n" +
                        "\n" +
                        "    private StaticMarkerBinder() {}\n" +
                        "\n" +
                        "    public static StaticMarkerBinder getSingleton() {\n" +
                        "        return SINGLETON;\n" +
                        "    }\n" +
                        "\n" +
                        "    public org.slf4j.IMarkerFactory getMarkerFactory() {\n" +
                        "        return markerFactory;\n" +
                        "    }\n" +
                        "}\n");

        ensureFile(sourceRoot, "org.slf4j.helpers.ThreadLocalMapOfStacks",
                "package org.slf4j.helpers;\n\n" +
                        "import java.util.ArrayDeque;\n" +
                        "import java.util.Deque;\n" +
                        "\n" +
                        "public final class ThreadLocalMapOfStacks {\n" +
                        "    private final ThreadLocal<Deque<String>> holder = ThreadLocal.withInitial(ArrayDeque::new);\n" +
                        "\n" +
                        "    public void pushByKey(String key, String value) {\n" +
                        "        holder.get().push(value);\n" +
                        "    }\n" +
                        "\n" +
                        "    public String popByKey(String key) {\n" +
                        "        Deque<String> deque = holder.get();\n" +
                        "        return deque.isEmpty() ? null : deque.pop();\n" +
                        "    }\n" +
                        "\n" +
                        "    public Deque<String> getCopyOfDequeByKey(String key) {\n" +
                        "        return new ArrayDeque<>(holder.get());\n" +
                        "    }\n" +
                        "\n" +
                        "    public void clearDequeByKey(String key) {\n" +
                        "        holder.get().clear();\n" +
                        "    }\n" +
                        "}\n");
    }

    private static void ensureFile(Path sourceRoot, String fullyQualifiedName, String source) throws IOException {
        Path file = sourceRoot.resolve(fullyQualifiedName.replace('.', '/') + ".java");
        if (Files.exists(file)) {
            return;
        }

        Files.createDirectories(file.getParent());
        CompilationUnit compilationUnit = StaticJavaParser.parse(source);
        Files.writeString(file, compilationUnit.toString(), StandardCharsets.UTF_8);
    }
}
