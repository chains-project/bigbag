package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String SLF4J_VERSION = "2.0.13";
    private static final String LOGBACK_VERSION = "1.4.6";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        rewritePom(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan project root: " + root, e);
        }
    }

    private static void rewritePom(Path pom) throws IOException {
        String original = Files.readString(pom, StandardCharsets.UTF_8);
        String updated = original;

        updated = replaceVersion(updated, "org.slf4j", "slf4j-api", SLF4J_VERSION);
        updated = replaceVersion(updated, "org.slf4j", "jul-to-slf4j", SLF4J_VERSION);
        updated = replaceVersion(updated, "org.slf4j", "jcl-over-slf4j", SLF4J_VERSION);
        updated = replaceVersion(updated, "org.slf4j", "log4j-over-slf4j", SLF4J_VERSION);
        updated = replaceVersion(updated, "ch.qos.logback", "logback-classic", LOGBACK_VERSION);
        updated = replaceVersion(updated, "ch.qos.logback", "logback-core", LOGBACK_VERSION);
        updated = replaceVersion(updated, "ch.qos.logback", "logback-access", LOGBACK_VERSION);

        updated = ensureDependencyManagement(updated);

        if (!updated.equals(original)) {
            Files.writeString(pom, updated, StandardCharsets.UTF_8);
        }
    }

    private static String replaceVersion(String pom, String groupId, String artifactId, String version) {
        Pattern pattern = Pattern.compile(
                "(?s)(<dependency>\\s*<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>" + Pattern.quote(artifactId) + "</artifactId>\\s*<version>)([^<]+)(</version>)");
        Matcher matcher = pattern.matcher(pom);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + version + matcher.group(3)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String ensureDependencyManagement(String pom) {
        if (pom.contains("<dependencyManagement>")) {
            return pom;
        }

        int insertAt = pom.indexOf("<dependencies>");
        if (insertAt < 0) {
            return pom;
        }

        String dependencyManagement = "<dependencyManagement>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>org.slf4j</groupId>\n"
                + "            <artifactId>slf4j-api</artifactId>\n"
                + "            <version>" + SLF4J_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.slf4j</groupId>\n"
                + "            <artifactId>jul-to-slf4j</artifactId>\n"
                + "            <version>" + SLF4J_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.slf4j</groupId>\n"
                + "            <artifactId>jcl-over-slf4j</artifactId>\n"
                + "            <version>" + SLF4J_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>org.slf4j</groupId>\n"
                + "            <artifactId>log4j-over-slf4j</artifactId>\n"
                + "            <version>" + SLF4J_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>ch.qos.logback</groupId>\n"
                + "            <artifactId>logback-classic</artifactId>\n"
                + "            <version>" + LOGBACK_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>ch.qos.logback</groupId>\n"
                + "            <artifactId>logback-core</artifactId>\n"
                + "            <version>" + LOGBACK_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "        <dependency>\n"
                + "            <groupId>ch.qos.logback</groupId>\n"
                + "            <artifactId>logback-access</artifactId>\n"
                + "            <version>" + LOGBACK_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</dependencyManagement>\n";

        return pom.substring(0, insertAt) + dependencyManagement + pom.substring(insertAt);
    }
}
