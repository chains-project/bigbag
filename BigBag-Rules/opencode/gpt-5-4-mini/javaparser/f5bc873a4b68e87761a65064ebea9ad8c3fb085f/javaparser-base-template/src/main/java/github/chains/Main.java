package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_MANIFEST_CONFIGURATION = "org.apache.maven.archiver.ManifestConfiguration";
    private static final String NEW_ARCHIVE_CONFIGURATION = "org.apache.maven.archiver.MavenArchiveConfiguration";
    private static final String MAVEN_CORE = "org.apache.maven:maven-core";
    private static final String MAVEN_ARTIFACT = "org.apache.maven:maven-artifact";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        for (Path pom : findFiles(root, "pom.xml")) {
            processModule(pom.getParent());
        }
    }

    private static void processModule(Path moduleDir) throws Exception {
        if (moduleDir == null) {
            return;
        }

        boolean needsMavenCore = false;
        boolean needsMavenArtifact = false;

        for (Path javaFile : findJavaFiles(moduleDir)) {
            CompilationUnit cu;
            try {
                cu = StaticJavaParser.parse(javaFile);
            } catch (Exception ex) {
                continue;
            }

            boolean changed = false;

            if (replaceManifestConfiguration(cu)) {
                changed = true;
            }

            if (containsType(cu, "org.apache.maven.project.MavenProject")) {
                needsMavenCore = true;
            }
            if (containsType(cu, "org.apache.maven.artifact.DependencyResolutionRequiredException")) {
                needsMavenArtifact = true;
            }

            if (changed) {
                Files.write(javaFile, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        }

        updatePom(moduleDir.resolve("pom.xml"), needsMavenCore, needsMavenArtifact);
    }

    private static boolean replaceManifestConfiguration(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (!importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(OLD_MANIFEST_CONFIGURATION)) {
                importDeclaration.setName(NEW_ARCHIVE_CONFIGURATION);
                changed = true;
            }
        }

        ManifestTypeVisitor visitor = new ManifestTypeVisitor();
        visitor.visit(cu, null);
        return changed || visitor.changed;
    }

    private static boolean containsType(CompilationUnit cu, String fqn) {
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (!importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> findFiles(Path root, String fileName) throws IOException {
        List<Path> result = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals(fileName))
                .forEach(result::add);
        }
        return result;
    }

    private static List<Path> findJavaFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"))
                .forEach(result::add);
        }
        return result;
    }

    private static void updatePom(Path pom, boolean needsMavenCore, boolean needsMavenArtifact) throws Exception {
        if (!Files.exists(pom)) {
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(pom.toFile());
        org.w3c.dom.Element project = doc.getDocumentElement();
        org.w3c.dom.NodeList dependencySections = project.getElementsByTagName("dependencies");
        if (dependencySections.getLength() == 0) {
            return;
        }

        org.w3c.dom.Element dependencies = (org.w3c.dom.Element) dependencySections.item(0);
        Set<String> existing = existingDependencies(dependencies);
        boolean changed = false;

        if (needsMavenCore && !existing.contains(MAVEN_CORE)) {
            dependencies.appendChild(createDependency(doc, "org.apache.maven", "maven-core", "provided"));
            changed = true;
        }
        if (needsMavenArtifact && !existing.contains(MAVEN_ARTIFACT)) {
            dependencies.appendChild(createDependency(doc, "org.apache.maven", "maven-artifact", "provided"));
            changed = true;
        }

        if (changed) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(pom.toFile()));
        }
    }

    private static Set<String> existingDependencies(org.w3c.dom.Element dependencies) {
        Set<String> existing = new HashSet<String>();
        org.w3c.dom.NodeList deps = dependencies.getElementsByTagName("dependency");
        for (int i = 0; i < deps.getLength(); i++) {
            org.w3c.dom.Element dep = (org.w3c.dom.Element) deps.item(i);
            String groupId = childText(dep, "groupId");
            String artifactId = childText(dep, "artifactId");
            if (groupId != null && artifactId != null) {
                existing.add(groupId + ":" + artifactId);
            }
        }
        return existing;
    }

    private static org.w3c.dom.Element createDependency(org.w3c.dom.Document doc, String groupId, String artifactId,
                                                         String scope) {
        org.w3c.dom.Element dependency = doc.createElement("dependency");
        appendChildText(doc, dependency, "groupId", groupId);
        appendChildText(doc, dependency, "artifactId", artifactId);
        appendChildText(doc, dependency, "scope", scope);
        return dependency;
    }

    private static void appendChildText(org.w3c.dom.Document doc, org.w3c.dom.Element parent, String name, String value) {
        org.w3c.dom.Element element = doc.createElement(name);
        element.appendChild(doc.createTextNode(value));
        parent.appendChild(element);
    }

    private static String childText(org.w3c.dom.Element parent, String name) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static final class ManifestTypeVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public ClassOrInterfaceType visit(ClassOrInterfaceType n, Void arg) {
            if (n.getNameAsString().equals("ManifestConfiguration")) {
                n.setName("MavenArchiveConfiguration");
                changed = true;
            }
            return (ClassOrInterfaceType) super.visit(n, arg);
        }
    }
}
