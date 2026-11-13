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
    private static final String LOGBACK_ACCESS_ARTIFACT_ID = "logback-access";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_LOGBACK_VERSION = "1.4.0";
    private static final String TARGET_SLF4J_VERSION = "2.0.17";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <project-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        try {
            List<Path> pomFiles = new ArrayList<>();
            try (var stream = Files.walk(root)) {
                stream.filter(path -> path.getFileName().toString().equals("pom.xml"))
                        .forEach(pomFiles::add);
            }

            boolean changed = false;
            for (Path pomFile : pomFiles) {
                changed |= updatePomIfNeeded(pomFile);
            }

            if (!changed) {
                System.out.println("No compatible Logback/SLF4J mismatch found.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply Logback/SLF4J compatibility transformation", e);
        }
    }

    private static boolean updatePomIfNeeded(Path pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document;
        try (var in = Files.newInputStream(pomFile)) {
            document = factory.newDocumentBuilder().parse(in);
        }
        document.getDocumentElement().normalize();

        boolean hasCompatibleLogback = hasDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID, version -> version.startsWith("1.4.") || version.startsWith("1.5."));
        if (!hasCompatibleLogback) {
            return false;
        }

        boolean updated = false;
        updated |= updateDependencyVersion(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID, TARGET_SLF4J_VERSION);
        updated |= ensureDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID, TARGET_LOGBACK_VERSION);
        updated |= ensureDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_ACCESS_ARTIFACT_ID, TARGET_LOGBACK_VERSION);
        if (updated) {
            writeDocument(document, pomFile);
        }
        return updated;
    }

    private static boolean hasDependencyVersion(Document document, String groupId, String artifactId, java.util.function.Predicate<String> predicate) {
        for (Element dependency : dependencyElements(document)) {
            String currentGroupId = childText(dependency, "groupId");
            String currentArtifactId = childText(dependency, "artifactId");
            String version = childText(dependency, "version");
            if (groupId.equals(currentGroupId) && artifactId.equals(currentArtifactId) && version != null && predicate.test(version)) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        boolean updated = false;
        for (Element dependency : dependencyElements(document)) {
            String currentGroupId = childText(dependency, "groupId");
            String currentArtifactId = childText(dependency, "artifactId");
            if (groupId.equals(currentGroupId) && artifactId.equals(currentArtifactId)) {
                Element versionElement = childElement(dependency, "version");
                if (versionElement != null) {
                    versionElement.setTextContent(newVersion);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        boolean updated = updateDependencyVersion(document, groupId, artifactId, newVersion);
        if (updated) {
            return true;
        }

        Element dependencies = firstChildElement(document.getDocumentElement(), "dependencies");
        if (dependencies == null) {
            return false;
        }

        Element dependency = document.createElement("dependency");
        Element group = document.createElement("groupId");
        group.setTextContent(groupId);
        Element artifact = document.createElement("artifactId");
        artifact.setTextContent(artifactId);
        Element version = document.createElement("version");
        version.setTextContent(newVersion);
        dependency.appendChild(group);
        dependency.appendChild(artifact);
        dependency.appendChild(version);
        dependencies.appendChild(document.createTextNode(System.lineSeparator() + "        "));
        dependencies.appendChild(dependency);
        dependencies.appendChild(document.createTextNode(System.lineSeparator() + "    "));
        return true;
    }

    private static List<Element> dependencyElements(Document document) {
        List<Element> dependencies = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                dependencies.add((Element) node);
            }
        }
        return dependencies;
    }

    private static Element childElement(Element parent, String childName) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Element firstChildElement(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && childName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String childText(Element parent, String childName) {
        Element child = childElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void writeDocument(Document document, Path pomFile) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pomFile)));
    }
}
