import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final List<String> MISSING_TYPES = List.of(
            "org.apache.maven.project.MavenProject",
            "org.apache.maven.artifact.DependencyResolutionRequiredException"
    );

    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "(?s)<dependency>.*?<groupId>org\\.apache\\.maven</groupId>.*?<artifactId>maven-plugin-api</artifactId>.*?<version>(.*?)</version>.*?</dependency>"
    );

    public static void main(String[] args) throws Exception {
        final Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        for (Path moduleRoot : findModuleRoots(sourceRoot)) {
            if (moduleNeedsMavenCore(moduleRoot)) {
                addMavenCoreDependency(moduleRoot.resolve("pom.xml"));
            }
        }
    }

    private static List<Path> findModuleRoots(Path root) throws IOException {
        final List<Path> modules = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return modules;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && "pom.xml".equals(path.getFileName().toString()))
                    .map(Path::getParent)
                    .distinct()
                    .forEach(modules::add);
        }
        return modules;
    }

    private static boolean moduleNeedsMavenCore(Path moduleRoot) throws IOException {
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);

        final Path mainJava = moduleRoot.resolve("src/main/java");
        final Path testJava = moduleRoot.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            launcher.addInputResource(mainJava.toString());
        }
        if (Files.isDirectory(testJava)) {
            launcher.addInputResource(testJava.toString());
        }
        if (launcher.getModelBuilder().getInputSources().isEmpty()) {
            return false;
        }

        final CtModel model = launcher.buildModel();
        for (CtTypeReference<?> reference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            final String qualifiedName = reference.getQualifiedName();
            if (MISSING_TYPES.contains(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private static void addMavenCoreDependency(Path pomFile) throws IOException {
        final String pom = Files.readString(pomFile, StandardCharsets.UTF_8);
        if (!pom.contains("<artifactId>maven-archiver</artifactId>") || hasDirectDependency(pom, "org.apache.maven", "maven-core")) {
            return;
        }

        final Matcher matcher = DEPENDENCY_BLOCK.matcher(pom);
        final String versionExpression = matcher.find() ? matcher.group(1).trim() : "${maven.version}";
        final String dependencyBlock =
                "    <dependency>\n"
                        + "      <groupId>org.apache.maven</groupId>\n"
                        + "      <artifactId>maven-core</artifactId>\n"
                        + "      <version>" + versionExpression + "</version>\n"
                        + "      <scope>provided</scope>\n"
                        + "    </dependency>\n";

        final String updated;
        final int dependenciesStart = pom.indexOf("<dependencies>");
        if (dependenciesStart >= 0) {
            final int insertAt = pom.indexOf("</dependencies>", dependenciesStart);
            if (insertAt < 0) {
                throw new IllegalStateException("Malformed pom.xml: missing </dependencies> in " + pomFile);
            }
            updated = pom.substring(0, insertAt) + dependencyBlock + pom.substring(insertAt);
        } else {
            final int insertAt = pom.lastIndexOf("</project>");
            if (insertAt < 0) {
                throw new IllegalStateException("Malformed pom.xml: missing </project> in " + pomFile);
            }
            updated = pom.substring(0, insertAt)
                    + "  <dependencies>\n"
                    + dependencyBlock
                    + "  </dependencies>\n"
                    + pom.substring(insertAt);
        }

        Files.writeString(pomFile, updated, StandardCharsets.UTF_8);
    }

    private static boolean hasDirectDependency(String pom, String groupId, String artifactId) {
        final int dependenciesStart = pom.indexOf("<dependencies>");
        if (dependenciesStart < 0) {
            return false;
        }
        final int dependenciesEnd = pom.indexOf("</dependencies>", dependenciesStart);
        if (dependenciesEnd < 0) {
            return false;
        }
        final String directDependencies = pom.substring(dependenciesStart, dependenciesEnd);
        return directDependencies.contains("<groupId>" + groupId + "</groupId>")
                && directDependencies.contains("<artifactId>" + artifactId + "</artifactId>");
    }
}
