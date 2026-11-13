package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String SLF4J_VERSION = "2.0.13";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_VERSION = "1.4.8";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        List<Path> poms = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                    .forEach(poms::add);
        }

        for (Path pom : poms) {
            updatePomIfNeeded(pom);
        }
    }

    private static void updatePomIfNeeded(Path pom) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
        document.getDocumentElement().normalize();

        boolean hasLogbackClassic148 = hasDependency(document, LOGBACK_GROUP_ID, "logback-classic", LOGBACK_VERSION);
        if (!hasLogbackClassic148) {
            return;
        }

        updateDependencyVersion(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID, SLF4J_VERSION);
        updateAllLogbackDependencyVersions(document, LOGBACK_VERSION);
        ensureDependency(document, LOGBACK_GROUP_ID, "logback-core", LOGBACK_VERSION);
        ensureDependency(document, LOGBACK_GROUP_ID, "logback-access", LOGBACK_VERSION);

        writeDocument(document, pom);
    }

    private static void ensureDependency(Document document, String groupId, String artifactId, String version) {
        if (!hasDependency(document, groupId, artifactId, version)) {
            addDependency(document, groupId, artifactId, version);
        }
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId, String version) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            if (matchesChildText(dependency, "groupId", groupId)
                    && matchesChildText(dependency, "artifactId", artifactId)
                    && matchesChildText(dependency, "version", version)) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String version) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        boolean updated = false;
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            if (matchesChildText(dependency, "groupId", groupId)
                    && matchesChildText(dependency, "artifactId", artifactId)) {
                setChildText(document, dependency, "version", version);
                updated = true;
            }
        }
        return updated;
    }

    private static void updateAllLogbackDependencyVersions(Document document, String version) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            if (matchesChildText(dependency, "groupId", LOGBACK_GROUP_ID)) {
                setChildText(document, dependency, "version", version);
            }
        }
    }

    private static void addDependency(Document document, String groupId, String artifactId, String version) {
        NodeList dependenciesNodes = document.getElementsByTagName("dependencies");
        if (dependenciesNodes.getLength() == 0) {
            return;
        }

        Element dependencies = (Element) dependenciesNodes.item(0);
        Element dependency = document.createElement("dependency");
        appendChildWithText(document, dependency, "groupId", groupId);
        appendChildWithText(document, dependency, "artifactId", artifactId);
        appendChildWithText(document, dependency, "version", version);
        dependencies.appendChild(document.createTextNode("\n        "));
        dependencies.appendChild(dependency);
        dependencies.appendChild(document.createTextNode("\n    "));
    }

    private static boolean matchesChildText(Node node, String childName, String expected) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (childName.equals(child.getNodeName()) && expected.equals(child.getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static void setChildText(Document document, Node parent, String childName, String value) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (childName.equals(child.getNodeName())) {
                child.setTextContent(value);
                return;
            }
        }
        appendChildWithText(document, parent, childName, value);
    }

    private static void appendChildWithText(Document document, Node parent, String childName, String value) {
        parent.appendChild(document.createTextNode("\n        "));
        Element child = document.createElement(childName);
        child.setTextContent(value);
        parent.appendChild(child);
        parent.appendChild(document.createTextNode("\n    "));
    }

    private static void writeDocument(Document document, Path pom) throws IOException {
        try {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pom)));
        } catch (Exception e) {
            throw new IOException("Failed to write " + pom, e);
        }
    }
}
