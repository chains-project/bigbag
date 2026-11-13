package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source root path");
        }

        Path sourceRoot = Paths.get(args[0]);
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        Files.walk(sourceRoot)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> {
                    try {
                        CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
                        LexicalPreservingPrinter.setup(cu);

                        boolean changed = false;
                        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                            if (call.getNameAsString().equals("parseEnchantment") && call.getArguments().isEmpty()) {
                                call.setName("getEnchant");
                                changed = true;
                            }
                        }

                        if (changed) {
                            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to transform " + path, e);
                    }
                });
    }
}
