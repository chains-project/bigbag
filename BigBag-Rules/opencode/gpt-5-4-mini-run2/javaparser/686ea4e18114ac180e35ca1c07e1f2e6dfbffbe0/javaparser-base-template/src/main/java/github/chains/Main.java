package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        TYPE_RENAMES.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        TYPE_RENAMES.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
        TYPE_RENAMES.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        TYPE_RENAMES.put("io.dropwizard.setup.AdminEnvironment", "io.dropwizard.core.setup.AdminEnvironment");
        TYPE_RENAMES.put("io.dropwizard.setup.AdminFactory", "io.dropwizard.core.setup.AdminFactory");
        TYPE_RENAMES.put("io.dropwizard.setup.EnvironmentCommand", "io.dropwizard.core.cli.EnvironmentCommand");

        TYPE_RENAMES.put("io.dropwizard.core.client.JerseyClientBuilder", "io.dropwizard.client.JerseyClientBuilder");
        TYPE_RENAMES.put("io.dropwizard.core.client.JerseyClientConfiguration", "io.dropwizard.client.JerseyClientConfiguration");
        TYPE_RENAMES.put("io.dropwizard.core.configuration.EnvironmentVariableSubstitutor", "io.dropwizard.configuration.EnvironmentVariableSubstitutor");
        TYPE_RENAMES.put("io.dropwizard.core.configuration.SubstitutingSourceProvider", "io.dropwizard.configuration.SubstitutingSourceProvider");
        TYPE_RENAMES.put("io.dropwizard.core.servlets.tasks.Task", "io.dropwizard.servlets.tasks.Task");
        TYPE_RENAMES.put("io.dropwizard.core.db.DataSourceFactory", "io.dropwizard.db.DataSourceFactory");
        TYPE_RENAMES.put("io.dropwizard.core.db.ManagedDataSource", "io.dropwizard.db.ManagedDataSource");
        TYPE_RENAMES.put("io.dropwizard.core.logging.AbstractAppenderFactory", "io.dropwizard.logging.AbstractAppenderFactory");
        TYPE_RENAMES.put("io.dropwizard.core.logging.async.AsyncAppenderFactory", "io.dropwizard.logging.async.AsyncAppenderFactory");
        TYPE_RENAMES.put("io.dropwizard.core.logging.filter.FilterFactory", "io.dropwizard.logging.filter.FilterFactory");
        TYPE_RENAMES.put("io.dropwizard.core.logging.filter.LevelFilterFactory", "io.dropwizard.logging.filter.LevelFilterFactory");
        TYPE_RENAMES.put("io.dropwizard.core.logging.layout.LayoutFactory", "io.dropwizard.logging.layout.LayoutFactory");
        TYPE_RENAMES.put("io.dropwizard.core.request.logging.layout.LogbackAccessRequestLayoutFactory", "io.dropwizard.request.logging.layout.LogbackAccessRequestLayoutFactory");
        TYPE_RENAMES.put("io.dropwizard.core.util.Strings", "io.dropwizard.util.Strings");

        TYPE_RENAMES.put("javax.ws.rs", "jakarta.ws.rs");
        TYPE_RENAMES.put("javax.validation", "jakarta.validation");
        TYPE_RENAMES.put("javax.servlet", "jakarta.servlet");
        TYPE_RENAMES.put("javax.annotation.Nullable", "jakarta.annotation.Nullable");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            LexicalPreservingPrinter.setup(cu);

            cu.getImports().forEach(importDeclaration -> {
                String imported = importDeclaration.getNameAsString();
                String replacement = rename(imported);
                if (replacement != null) {
                    importDeclaration.setName(replacement);
                }
            });

            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ClassOrInterfaceType type, Void arg) {
                    super.visit(type, arg);
                    String replacement = rename(type.asString());
                    if (replacement != null) {
                        type.replace(StaticJavaParser.parseClassOrInterfaceType(replacement));
                    }
                }

                @Override
                public void visit(ObjectCreationExpr expr, Void arg) {
                    super.visit(expr, arg);
                    String replacement = rename(expr.getType().asString());
                    if (replacement != null) {
                        expr.setType(StaticJavaParser.parseClassOrInterfaceType(replacement));
                    }
                }
            }, null);

            Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static String rename(String name) {
        String exact = TYPE_RENAMES.get(name);
        if (exact != null) {
            return exact;
        }

        return null;
    }
}
