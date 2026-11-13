package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String REQUIRED_SLF4J_VERSION = "2.0.0";
    private static final String REQUIRED_LOGBACK_CORE_VERSION = "1.4.0";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source directory");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> modified = new ArrayList<>();

        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                    .forEach(path -> {
                        try {
                            if (updatePom(path)) {
                                modified.add(path);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to update " + path, e);
                        }
                    });
        }

        for (Path path : modified) {
            System.out.println("Updated " + root.relativize(path));
        }
    }

    private static boolean updatePom(Path pomPath) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        document.getDocumentElement().normalize();

        String logbackVersion = findDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID);
        if (logbackVersion == null || compareVersion(logbackVersion, "1.4.0") < 0) {
            return false;
        }

        boolean changed = false;
        changed |= ensureDependencyVersion(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID, REQUIRED_SLF4J_VERSION, false);
        changed |= ensureDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID, REQUIRED_LOGBACK_CORE_VERSION, true);

        if (changed) {
            writeDocument(document, pomPath);
        }
        return changed;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String requiredVersion, boolean createIfMissing) {
        Element dependency = findDependency(document, groupId, artifactId);
        if (dependency == null) {
            if (!createIfMissing) {
                return false;
            }
            dependency = addDependency(document, groupId, artifactId, requiredVersion);
            return dependency != null;
        }

        Element versionElement = childElement(dependency, "version");
        if (versionElement == null) {
            versionElement = document.createElement("version");
            dependency.appendChild(versionElement);
        }

        String currentVersion = versionElement.getTextContent() == null ? "" : versionElement.getTextContent().trim();
        if (currentVersion.isEmpty() || compareVersion(currentVersion, requiredVersion) < 0) {
            versionElement.setTextContent(requiredVersion);
            return true;
        }
        return false;
    }

    private static Element addDependency(Document document, String groupId, String artifactId, String version) {
        Element project = document.getDocumentElement();
        Element dependencies = childElement(project, "dependencies");
        if (dependencies == null) {
            dependencies = document.createElement("dependencies");
            project.appendChild(dependencies);
        }

        Element dependency = document.createElement("dependency");
        Element group = document.createElement("groupId");
        group.setTextContent(groupId);
        Element artifact = document.createElement("artifactId");
        artifact.setTextContent(artifactId);
        Element versionElement = document.createElement("version");
        versionElement.setTextContent(version);

        dependency.appendChild(group);
        dependency.appendChild(artifact);
        dependency.appendChild(versionElement);
        dependencies.appendChild(dependency);
        return dependency;
    }

    private static Element findDependency(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node instanceof Element dependency) {
                String currentGroupId = textOfChild(dependency, "groupId");
                String currentArtifactId = textOfChild(dependency, "artifactId");
                if (groupId.equals(currentGroupId) && artifactId.equals(currentArtifactId)) {
                    return dependency;
                }
            }
        }
        return null;
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        Element dependency = findDependency(document, groupId, artifactId);
        return dependency == null ? null : textOfChild(dependency, "version");
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static String textOfChild(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void writeDocument(Document document, Path pomPath) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
    }

    private static int compareVersion(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftPart = i < leftParts.length ? parseInt(leftParts[i]) : 0;
            int rightPart = i < rightParts.length ? parseInt(rightParts[i]) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int parseInt(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
