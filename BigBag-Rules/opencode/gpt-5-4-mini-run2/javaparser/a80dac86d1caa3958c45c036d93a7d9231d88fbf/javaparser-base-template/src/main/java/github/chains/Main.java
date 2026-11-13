package github.chains;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.0";

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName() != null && "pom.xml".equals(path.getFileName().toString()))
                    .forEach(Main::updatePom);
        }
    }

    private static void updatePom(Path pom) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
            document.getDocumentElement().normalize();

            if (!hasLogback14Plus(document)) {
                return;
            }

            boolean changed = updateSlf4jDependency(document);
            if (changed) {
                write(document, pom.toFile());
            }
        } catch (Exception ignored) {
            // Skip non-POM or malformed files.
        }
    }

    private static boolean hasLogback14Plus(Document document) {
        for (Element dependency : dependencyElements(document)) {
            if (LOGBACK_GROUP_ID.equals(textOf(dependency, "groupId"))
                    && LOGBACK_ARTIFACT_ID.equals(textOf(dependency, "artifactId"))
                    && isVersion14OrNewer(textOf(dependency, "version"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependency(Document document) {
        boolean changed = false;
        Element properties = childElement(document.getDocumentElement(), "properties");
        for (Element dependency : dependencyElements(document)) {
            if (!SLF4J_GROUP_ID.equals(textOf(dependency, "groupId"))
                    || !SLF4J_ARTIFACT_ID.equals(textOf(dependency, "artifactId"))) {
                continue;
            }

            Element versionElement = childElement(dependency, "version");
            if (versionElement == null) {
                continue;
            }

            String version = versionElement.getTextContent().trim();
            if (version.startsWith("${") && version.endsWith("}")) {
                String propertyName = version.substring(2, version.length() - 1);
                if (properties != null) {
                    Element property = childElement(properties, propertyName);
                    if (property != null && isLegacySlf4j(property.getTextContent().trim())) {
                        property.setTextContent(TARGET_SLF4J_VERSION);
                        changed = true;
                    }
                }
            } else if (isLegacySlf4j(version)) {
                versionElement.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isLegacySlf4j(String version) {
        return version != null && version.startsWith("1.7");
    }

    private static boolean isVersion14OrNewer(String version) {
        return version != null && version.matches("1\\.(4|[5-9]\\d*)(\\..*)?");
    }

    private static List<Element> dependencyElements(Document document) {
        NodeList nodes = document.getElementsByTagName("dependency");
        List<Element> dependencies = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                dependencies.add((Element) node);
            }
        }
        return dependencies;
    }

    private static String textOf(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && tagName.equals(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void write(Document document, File file) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(file));
    }
}
