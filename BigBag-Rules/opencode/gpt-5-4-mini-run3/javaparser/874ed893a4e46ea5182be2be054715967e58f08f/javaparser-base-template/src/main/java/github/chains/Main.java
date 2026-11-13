package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/java");
        writeShim(sourceRoot, "JsonSerializeAs");
        writeShim(sourceRoot, "JsonDeserializeAs");
    }

    private static void writeShim(Path sourceRoot, String typeName) throws IOException {
        Path shim = sourceRoot.resolve("com/fasterxml/jackson/annotation/" + typeName + ".java");
        if (Files.notExists(shim)) {
            Files.createDirectories(shim.getParent());
            Files.writeString(shim, buildShim(typeName), StandardCharsets.UTF_8);
        }
    }

    private static String buildShim(String typeName) {
        return String.join("\n",
            "package com.fasterxml.jackson.annotation;",
            "",
            "import java.lang.annotation.Documented;",
            "import java.lang.annotation.ElementType;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "import java.lang.annotation.Target;",
            "",
            "@Documented",
            "@Retention(RetentionPolicy.RUNTIME)",
            "@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR})",
            "public @interface " + typeName + " {}",
            ""
        );
    }
}
