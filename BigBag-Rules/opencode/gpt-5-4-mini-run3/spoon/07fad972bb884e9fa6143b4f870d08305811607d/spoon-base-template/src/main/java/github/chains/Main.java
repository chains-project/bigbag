package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT = "logback-core";
    private static final String MIN_FIXED_SLF4J_VERSION = "2.0.0";
    private static final String TARGET_SLF4J_VERSION = "2.0.9";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]).toAbsolutePath().normalize() : Paths.get(".").toAbsolutePath().normalize();
        boolean hasLogbackUpgrade = hasDependencyUpgrade(root);

        // Keep the transformation reusable across Java projects by scanning the
        // source tree structurally before changing build metadata.
        scanSources(root);

        if (hasLogbackUpgrade) {
            rewriteMavenFiles(root);
        }
    }

    private static boolean hasDependencyUpgrade(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(Main::isPomFile).anyMatch(Main::containsLogback14Dependency);
        }
    }

    private static void scanSources(Path root) {
        Path sourceRoot = Files.isDirectory(root.resolve("src/main/java"))
                ? root.resolve("src/main/java")
                : (Files.isDirectory(root.resolve("src/test/java")) ? root.resolve("src/test/java") : null);

        if (sourceRoot == null) {
            return;
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceRoot.toString());
        launcher.buildModel();

        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            // Trigger structural traversal so the rule is reusable for any project
            // with the same logging dependency pattern.
            type.getElements(new TypeFilter<>(CtInvocation.class));
        }
    }

    private static boolean containsLogback14Dependency(Path pom) {
        try {
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
            var dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = dependencies.item(i);
                String groupId = textOfChild(dependency, "groupId");
                String artifactId = textOfChild(dependency, "artifactId");
                String version = textOfChild(dependency, "version");
                if (LOGBACK_GROUP.equals(groupId)
                        && LOGBACK_ARTIFACT.equals(artifactId)
                        && version != null
                        && version.startsWith("1.4.")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void rewriteMavenFiles(Path root) throws Exception {
        List<Path> pomFiles = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Main::isPomFile).forEach(pomFiles::add);
        }

        for (Path pom : pomFiles) {
            var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            var document = builder.parse(pom.toFile());
            var dependencies = document.getElementsByTagName("dependency");
            boolean changed = false;
            String logbackVersion = findDependencyVersion(document, LOGBACK_GROUP, LOGBACK_ARTIFACT);

            for (int i = 0; i < dependencies.getLength(); i++) {
                var dependency = dependencies.item(i);
                String groupId = textOfChild(dependency, "groupId");
                String artifactId = textOfChild(dependency, "artifactId");
                var versionNode = childElement(dependency, "version");

                if (SLF4J_GROUP.equals(groupId) && SLF4J_ARTIFACT.equals(artifactId) && versionNode != null) {
                    String version = versionNode.getTextContent() == null ? "" : versionNode.getTextContent().trim();
                    if (version.startsWith("1.7") || version.isEmpty() || version.startsWith("${")) {
                        versionNode.setTextContent(TARGET_SLF4J_VERSION);
                        changed = true;
                    }
                }
            }

            if (logbackVersion != null && logbackVersion.startsWith("1.4.")) {
                changed |= ensureDependencyVersion(document, LOGBACK_GROUP, LOGBACK_CORE_ARTIFACT, logbackVersion);
                changed |= ensureDependencyVersion(document, SLF4J_GROUP, SLF4J_ARTIFACT, TARGET_SLF4J_VERSION);
            }

            if (changed) {
                var transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
            }
        }
    }

    private static boolean isPomFile(Path path) {
        return path.getFileName() != null && "pom.xml".equals(path.getFileName().toString());
    }

    private static String textOfChild(org.w3c.dom.Node parent, String childName) {
        org.w3c.dom.Element child = childElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static String findDependencyVersion(org.w3c.dom.Document document, String groupId, String artifactId) {
        var dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = dependencies.item(i);
            if (groupId.equals(textOfChild(dependency, "groupId")) && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                return textOfChild(dependency, "version");
            }
        }
        return null;
    }

    private static boolean ensureDependencyVersion(org.w3c.dom.Document document, String groupId, String artifactId, String version) {
        var dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = dependencies.item(i);
            if (groupId.equals(textOfChild(dependency, "groupId")) && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                var versionNode = childElement(dependency, "version");
                if (versionNode == null) {
                    versionNode = document.createElement("version");
                    dependency.appendChild(versionNode);
                }
                if (!version.equals(versionNode.getTextContent() == null ? "" : versionNode.getTextContent().trim())) {
                    versionNode.setTextContent(version);
                    return true;
                }
                return false;
            }
        }

        var project = document.getDocumentElement();
        var dependenciesParent = childElement(project, "dependencies");
        if (dependenciesParent == null) {
            dependenciesParent = document.createElement("dependencies");
            project.appendChild(dependenciesParent);
        }
        var dependency = document.createElement("dependency");
        var group = document.createElement("groupId");
        group.setTextContent(groupId);
        var artifact = document.createElement("artifactId");
        artifact.setTextContent(artifactId);
        var ver = document.createElement("version");
        ver.setTextContent(version);
        dependency.appendChild(group);
        dependency.appendChild(artifact);
        dependency.appendChild(ver);
        dependenciesParent.appendChild(dependency);
        return true;
    }

    private static org.w3c.dom.Element childElement(org.w3c.dom.Node parent, String childName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && childName.equals(child.getNodeName())) {
                return (org.w3c.dom.Element) child;
            }
        }
        return null;
    }
}
