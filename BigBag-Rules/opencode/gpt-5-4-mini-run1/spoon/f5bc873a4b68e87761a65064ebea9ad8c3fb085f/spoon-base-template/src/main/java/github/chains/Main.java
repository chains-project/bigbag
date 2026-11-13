package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

  private static final Pattern OLD_PARSE = Pattern.compile("(?<![\\w$])parseOutputTimestamp\\s*\\(([^)]*)\\)");
  private static final String MAVEN_CORE_DEP =
    "    <dependency>\n"
      + "      <groupId>org.apache.maven</groupId>\n"
      + "      <artifactId>maven-core</artifactId>\n"
      + "      <version>${maven.version}</version>\n"
      + "    </dependency>\n";

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
    }

    final Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
    final Path outputDir = Paths.get(args.length > 1 ? args[1] : args[0] + "-fixed").toAbsolutePath().normalize();
    Files.createDirectories(outputDir);

    final List<Path> javaFiles = new ArrayList<>();
    final Set<Path> moduleRootsNeedingMavenCore = new LinkedHashSet<>();

    try (Stream<Path> paths = Files.walk(inputDir)) {
      paths.filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(javaFiles::add);
    }

    for (Path source : javaFiles) {
      final String text = Files.readString(source, StandardCharsets.UTF_8);
      final Path moduleRoot = findModuleRoot(source);
      if (text.contains("org.apache.maven.project.MavenProject")
        || text.contains("org.apache.maven.artifact.DependencyResolutionRequiredException")) {
        moduleRootsNeedingMavenCore.add(moduleRoot);
      }

      final Path target = outputDir.resolve(inputDir.relativize(source));
      Files.createDirectories(target.getParent());
      Files.writeString(target, fixJava(text), StandardCharsets.UTF_8);
    }

    copyNonJavaFiles(inputDir, outputDir);
    for (Path moduleRoot : moduleRootsNeedingMavenCore) {
      patchPom(outputDir.resolve(inputDir.relativize(moduleRoot)));
    }
  }

  private static String fixJava(String text) {
    String fixed = text.replace("configureReproducible(", "configureReproducibleBuild(");
    return OLD_PARSE.matcher(fixed)
      .replaceAll("parseBuildOutputTimestamp($1).map(java.util.Date::from).orElse(null)");
  }

  private static Path findModuleRoot(Path source) {
    Path current = source.getParent();
    while (current != null) {
      if (current.getFileName() != null && "src".equals(current.getFileName().toString())) {
        return current.getParent();
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not determine module root for " + source);
  }

  private static void copyNonJavaFiles(Path inputDir, Path outputDir) throws IOException {
    try (Stream<Path> paths = Files.walk(inputDir)) {
      paths.filter(Files::isRegularFile)
        .filter(path -> !path.toString().endsWith(".java"))
        .forEach(path -> {
          try {
            final Path target = outputDir.resolve(inputDir.relativize(path));
            Files.createDirectories(target.getParent());
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            throw new IllegalStateException("Failed to copy " + path, e);
          }
        });
    }
  }

  private static void patchPom(Path moduleRoot) throws IOException {
    final Path pom = moduleRoot.resolve("pom.xml");
    if (!Files.exists(pom)) {
      return;
    }

    final String current = Files.readString(pom, StandardCharsets.UTF_8);
    if (current.contains("<groupId>org.apache.maven</groupId>\n      <artifactId>maven-core</artifactId>\n      <version>${maven.version}</version>\n      <scope>provided</scope>")) {
      return;
    }

    final String marker = "    <dependency>\n      <groupId>org.apache.maven</groupId>\n      <artifactId>maven-model</artifactId>\n      <version>${maven.version}</version>\n      <scope>provided</scope>\n    </dependency>\n";
    final String updated;
    if (current.contains(marker)) {
      updated = current.replace(marker, marker + MAVEN_CORE_DEP);
    } else {
      final int depsEnd = current.indexOf("</dependencies>");
      if (depsEnd < 0) {
        return;
      }
      updated = current.substring(0, depsEnd) + MAVEN_CORE_DEP + current.substring(depsEnd);
    }
    Files.writeString(pom, updated, StandardCharsets.UTF_8);
  }
}
