package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {

    private static final String OLD_TYPE = "eu.europa.esig.dss.pades.CertificationPermission";
    private static final String NEW_TYPE = "eu.europa.esig.dss.enumerations.CertificationPermission";
    private static final String NEW_ARTIFACT_ID = "dss-enumerations";
    private static final String NEW_GROUP_ID = "eu.europa.ec.joinup.sd-dss";
    private static final Pattern POM_DEPENDENCY = Pattern.compile(
            "<dependency>\\s*<groupId>(.*?)</groupId>\\s*<artifactId>(.*?)</artifactId>(?:\\s*<version>(.*?)</version>)?.*?</dependency>",
            Pattern.DOTALL);

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try {
            boolean[] changed = {false};
            boolean[] needsDependency = {false};

            Files.walk(sourceRoot)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> {
                        needsDependency[0] |= fileUsesRelocatedType(path);
                        changed[0] |= rewriteFile(path);
                    });

            if (needsDependency[0]) {
                addMissingDependency(findPom(sourceRoot).orElse(null));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite sources under " + sourceRoot, e);
        }
    }

    private static boolean fileUsesRelocatedType(Path file) {
        try {
            String source = Files.readString(file);
            return source.contains(OLD_TYPE) || source.contains(NEW_TYPE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect " + file, e);
        }
    }

    private static boolean rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            for (int i = 0; i < cu.getImports().size(); i++) {
                if (cu.getImports().get(i).getNameAsString().equals(OLD_TYPE)) {
                    cu.getImports().get(i).setName(NEW_TYPE);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (OLD_TYPE.equals(type.toString())) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                    changed = true;
                }
            }

            for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
                String text = fieldAccessExpr.toString();
                if (text.startsWith(OLD_TYPE + ".")) {
                    Expression replacement = StaticJavaParser.parseExpression(text.replace(OLD_TYPE, NEW_TYPE));
                    fieldAccessExpr.replace(replacement);
                    changed = true;
                }
            }

            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static Optional<Path> findPom(Path start) throws IOException {
        Path current = start.toAbsolutePath();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return Optional.of(pom);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static void addMissingDependency(Path pom) {
        if (pom == null) {
            return;
        }
        try {
            String pomText = Files.readString(pom, StandardCharsets.UTF_8);
            if (pomText.contains("<artifactId>" + NEW_ARTIFACT_ID + "</artifactId>")) {
                return;
            }

            String padesVersion = extractDependencyVersion(pomText, NEW_GROUP_ID, "dss-pades");
            if (padesVersion == null || padesVersion.isEmpty()) {
                return;
            }

            String dependencyBlock = "        <dependency>\n"
                    + "            <groupId>" + NEW_GROUP_ID + "</groupId>\n"
                    + "            <artifactId>" + NEW_ARTIFACT_ID + "</artifactId>\n"
                    + "            <version>" + padesVersion + "</version>\n"
                    + "        </dependency>\n";
            String updated = pomText.replace("    </dependencies>", dependencyBlock + "    </dependencies>");
            Files.writeString(pom, updated, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to update pom.xml at " + pom, e);
        }
    }

    private static String extractDependencyVersion(String pomText, String groupId, String artifactId) {
        Matcher matcher = POM_DEPENDENCY.matcher(pomText);
        while (matcher.find()) {
            if (groupId.equals(matcher.group(1).trim()) && artifactId.equals(matcher.group(2).trim())) {
                return matcher.group(3) == null ? null : matcher.group(3).trim();
            }
        }
        return null;
    }
}
