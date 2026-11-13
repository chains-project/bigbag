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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String LOGBACK_CLASSIC_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_API_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.7";

    public static void main(String[] args) throws Exception {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        List<Path> pomFiles = new ArrayList<>();

        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.getFileName() != null && "pom.xml".equals(path.getFileName().toString()))
                    .forEach(pomFiles::add);
        }

        for (Path pomFile : pomFiles) {
            rewritePomIfNeeded(pomFile);
        }
    }

    private static void rewritePomIfNeeded(Path pomFile) throws Exception {
        Document document = parseXml(pomFile);
        if (!usesLogbackClassic14Plus(document)) {
            return;
        }

        boolean changed = updateSlf4jApiDependencies(document);
        changed |= updateSlf4jApiProperties(document);
        changed |= ensureLogbackCoreCompatibility(document);
        if (!changed) {
            return;
        }

        writeXml(document, pomFile);
    }

    private static Document parseXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(false);
        try (InputStream inputStream = Files.newInputStream(file)) {
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private static void writeXml(Document document, Path file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
    }

    private static boolean usesLogbackClassic14Plus(Document document) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element dependency = (Element) node;
            if (!LOGBACK_CLASSIC_GROUP_ID.equals(childText(dependency, "groupId"))
                    || !LOGBACK_CLASSIC_ARTIFACT_ID.equals(childText(dependency, "artifactId"))) {
                continue;
            }

            int[] version = parseMajorMinor(childText(dependency, "version"));
            if (version != null && (version[0] > 1 || (version[0] == 1 && version[1] >= 4))) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateSlf4jApiDependencies(Document document) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element dependency = (Element) node;
            if (!SLF4J_GROUP_ID.equals(childText(dependency, "groupId"))
                    || !SLF4J_API_ARTIFACT_ID.equals(childText(dependency, "artifactId"))) {
                continue;
            }

            Element versionElement = directChild(dependency, "version");
            if (versionElement == null) {
                continue;
            }

            String versionText = versionElement.getTextContent().trim();
            if (isLegacySlf4jVersion(versionText)) {
                versionElement.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String version) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element dependency = (Element) node;
            if (!groupId.equals(childText(dependency, "groupId")) || !artifactId.equals(childText(dependency, "artifactId"))) {
                continue;
            }

            Element versionElement = directChild(dependency, "version");
            if (versionElement == null) {
                return false;
            }

            if (!version.equals(versionElement.getTextContent().trim())) {
                versionElement.setTextContent(version);
                return true;
            }
            return false;
        }

        Element dependenciesElement = directChild(document.getDocumentElement(), "dependencies");
        if (dependenciesElement == null) {
            return false;
        }

        Element dependency = document.createElement("dependency");
        Element newGroupId = document.createElement("groupId");
        newGroupId.setTextContent(groupId);
        Element newArtifactId = document.createElement("artifactId");
        newArtifactId.setTextContent(artifactId);
        Element newVersion = document.createElement("version");
        newVersion.setTextContent(version);

        dependency.appendChild(newGroupId);
        dependency.appendChild(newArtifactId);
        dependency.appendChild(newVersion);
        dependenciesElement.appendChild(document.createTextNode("\n        "));
        dependenciesElement.appendChild(dependency);
        dependenciesElement.appendChild(document.createTextNode("\n    "));
        return true;
    }

    private static boolean updateSlf4jApiProperties(Document document) {
        boolean changed = false;
        Element properties = directChild(document.getDocumentElement(), "properties");
        if (properties == null) {
            return false;
        }

        NodeList children = properties.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element property = (Element) node;
            String propertyName = property.getTagName();
            if (!propertyName.toLowerCase().contains("slf4j")) {
                continue;
            }

            int[] version = parseMajorMinor(property.getTextContent());
            if (version != null && version[0] == 1 && version[1] <= 7) {
                property.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean ensureLogbackCoreCompatibility(Document document) {
        if (!usesLogbackClassic14Plus(document)) {
            return false;
        }

        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }

            Element dependency = (Element) node;
            if (!LOGBACK_CLASSIC_GROUP_ID.equals(childText(dependency, "groupId"))
                    || !"logback-core".equals(childText(dependency, "artifactId"))) {
                continue;
            }

            Element versionElement = directChild(dependency, "version");
            if (versionElement == null) {
                continue;
            }

            if (!"1.4.1".equals(versionElement.getTextContent().trim())) {
                versionElement.setTextContent("1.4.1");
                return true;
            }
            return false;
        }

        Element dependenciesElement = directChild(document.getDocumentElement(), "dependencies");
        if (dependenciesElement == null) {
            return false;
        }

        Element dependency = document.createElement("dependency");
        Element groupId = document.createElement("groupId");
        groupId.setTextContent(LOGBACK_CLASSIC_GROUP_ID);
        Element artifactId = document.createElement("artifactId");
        artifactId.setTextContent("logback-core");
        Element version = document.createElement("version");
        version.setTextContent("1.4.1");

        dependency.appendChild(groupId);
        dependency.appendChild(artifactId);
        dependency.appendChild(version);
        dependenciesElement.appendChild(document.createTextNode("\n        "));
        dependenciesElement.appendChild(dependency);
        dependenciesElement.appendChild(document.createTextNode("\n    "));
        return true;
    }

    private static boolean isLegacySlf4jVersion(String versionText) {
        int[] version = parseMajorMinor(versionText);
        return version != null && version[0] == 1 && version[1] <= 7;
    }

    private static int[] parseMajorMinor(String versionText) {
        String normalized = versionText == null ? "" : versionText.trim();
        if (normalized.isEmpty() || normalized.startsWith("${")) {
            return null;
        }

        String[] parts = normalized.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String childText(Element parent, String childName) {
        Element child = directChild(parent, childName);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && childName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
