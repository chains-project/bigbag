package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            SnakeYamlUpgradeTransformer transformer = new SnakeYamlUpgradeTransformer();
            int changedFiles = 0;
            for (Path file : javaFiles) {
                if (transformer.rewrite(file)) {
                    changedFiles++;
                }
            }
            System.out.println("Updated " + changedFiles + " file(s)");
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse source root: " + sourceRoot, e);
        }
    }

    private static final class SnakeYamlUpgradeTransformer extends ModifierVisitor<Void> {
        private static final String CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.Constructor";
        private static final String SAFE_CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.SafeConstructor";
        private static final String CUSTOM_CLASS_LOADER_CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor";
        private static final String REPRESENTER_FQN = "org.yaml.snakeyaml.representer.Representer";
        private static final String YAML_FQN = "org.yaml.snakeyaml.Yaml";
        private static final String LOADER_OPTIONS_FQN = "org.yaml.snakeyaml.LoaderOptions";

        boolean rewrite(Path file) throws IOException {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(compilationUnit);
            visit(compilationUnit, null);

            String updatedSource = LexicalPreservingPrinter.print(compilationUnit);
            String originalSource = Files.readString(file, StandardCharsets.UTF_8);
            if (Objects.equals(originalSource, updatedSource)) {
                return false;
            }

            Files.writeString(file, updatedSource, StandardCharsets.UTF_8);
            return true;
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);

            String typeName = n.getType().asString();
            if (isSnakeYamlConstructor(typeName)) {
                if (!endsWithLoaderOptions(n)) {
                    n.getArguments().add(createLoaderOptionsExpression());
                }
                return n;
            }

            if (isLoaderOptions(typeName) && n.getArguments().isEmpty()) {
                return createLoaderOptionsExpression();
            }

            if (isRepresenter(typeName) && n.getArguments().isEmpty()) {
                return StaticJavaParser.parseExpression("new org.yaml.snakeyaml.representer.Representer(new org.yaml.snakeyaml.DumperOptions())");
            }

            if (isYaml(typeName) && n.getArguments().isEmpty()) {
                return StaticJavaParser.parseExpression(
                        "new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.Constructor(new org.yaml.snakeyaml.LoaderOptions() {{ setTagInspector(new org.yaml.snakeyaml.inspector.TrustedTagInspector()); }}))");
            }

            return n;
        }

        private boolean isSnakeYamlConstructor(String typeName) {
            return CONSTRUCTOR_FQN.equals(typeName)
                    || SAFE_CONSTRUCTOR_FQN.equals(typeName)
                    || CUSTOM_CLASS_LOADER_CONSTRUCTOR_FQN.equals(typeName)
                    || "Constructor".equals(typeName)
                    || "SafeConstructor".equals(typeName)
                    || "CustomClassLoaderConstructor".equals(typeName);
        }

        private boolean isRepresenter(String typeName) {
            return REPRESENTER_FQN.equals(typeName) || "Representer".equals(typeName);
        }

        private boolean isYaml(String typeName) {
            return YAML_FQN.equals(typeName) || "Yaml".equals(typeName);
        }

        private boolean isLoaderOptions(String typeName) {
            return LOADER_OPTIONS_FQN.equals(typeName) || "LoaderOptions".equals(typeName);
        }

        private boolean endsWithLoaderOptions(ObjectCreationExpr expr) {
            if (expr.getArguments().isEmpty()) {
                return false;
            }
            Expression lastArgument = expr.getArguments().get(expr.getArguments().size() - 1);
            String text = lastArgument.toString();
            return text.contains("LoaderOptions");
        }

        private Expression createLoaderOptionsExpression() {
            return StaticJavaParser.parseExpression("new org.yaml.snakeyaml.LoaderOptions() {{ setTagInspector(new org.yaml.snakeyaml.inspector.TrustedTagInspector()); }}");
        }
    }
}
