package github.chains;

import spoon.Launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern MVC_NAMESPACE = Pattern.compile("\\bjavax\\.mvc(?=[\\.\\s(])");

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected an input source directory");
        }

        Path inputRoot = Path.of(args[0]);
        Path outputRoot = args.length > 1 ? Path.of(args[1]) : inputRoot;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputRoot.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        Files.walkFileTree(inputRoot, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                String source = Files.readString(file, StandardCharsets.UTF_8);
                String transformed = rewriteMvcNamespace(source);
                if (source.equals(transformed)) {
                    return FileVisitResult.CONTINUE;
                }

                Path targetFile = outputRoot.equals(inputRoot) ? file : outputRoot.resolve(inputRoot.relativize(file));
                Files.createDirectories(targetFile.getParent());
                Files.writeString(targetFile, transformed, StandardCharsets.UTF_8);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String rewriteMvcNamespace(String source) {
        String rewritten = MVC_NAMESPACE.matcher(source).replaceAll("jakarta.mvc");
        return dedupeConsecutiveImports(rewritten);
    }

    private static String dedupeConsecutiveImports(String source) {
        String[] lines = source.split("\\R", -1);
        StringBuilder out = new StringBuilder(source.length());
        LinkedHashSet<String> importBlock = new LinkedHashSet<>();
        boolean inImportBlock = false;

        for (String line : lines) {
            if (line.startsWith("import ")) {
                inImportBlock = true;
                importBlock.add(line);
                continue;
            }

            if (inImportBlock) {
                for (String importLine : importBlock) {
                    out.append(importLine).append('\n');
                }
                importBlock.clear();
                inImportBlock = false;
            }

            out.append(line).append('\n');
        }

        if (inImportBlock) {
            for (String importLine : importBlock) {
                out.append(importLine).append('\n');
            }
        }

        if (!source.endsWith("\n") && out.length() > 0) {
            out.setLength(out.length() - 1);
        }

        return out.toString();
    }
}
