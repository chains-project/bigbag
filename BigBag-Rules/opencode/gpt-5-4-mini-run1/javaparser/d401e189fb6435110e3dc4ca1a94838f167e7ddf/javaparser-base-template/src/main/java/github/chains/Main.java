package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.xml.XMLConstants;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.5";

    public static void main(String[] args) {
        final Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.walk(root)) {
            final List<Path> poms = paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());

            for (Path pom : poms) {
                transformPom(pom);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan " + root, e);
        }
    }

    private static void transformPom(Path pom) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);

            final Document document = factory.newDocumentBuilder().parse(pom.toFile());
            final Map<String, String> properties = readProperties(document);

            if (!usesLogbackClassic(document, properties)) {
                return;
            }

            boolean changed = updateSlf4jDependencies(document, properties);
            if (changed) {
                writeDocument(document, pom);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + pom, e);
        }
    }

    private static Map<String, String> readProperties(Document document) {
        final Map<String, String> properties = new HashMap<>();
        final NodeList propertySections = document.getElementsByTagNameNS("*", "properties");
        if (propertySections.getLength() == 0) {
            return properties;
        }

        final Node propertiesNode = propertySections.item(0);
        final NodeList children = propertiesNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                properties.put(child.getLocalName(), child.getTextContent().trim());
            }
        }
        return properties;
    }

    private static boolean usesLogbackClassic(Document document, Map<String, String> properties) {
        for (Element dependency : dependencies(document)) {
            if (matches(dependency, LOGBACK_GROUP, LOGBACK_ARTIFACT)) {
                final String version = resolvedVersion(dependency, properties);
                if (version != null && isAtLeast(version, 1, 4, 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependencies(Document document, Map<String, String> properties) {
        boolean changed = false;
        for (Element dependency : dependencies(document)) {
            if (!matches(dependency, SLF4J_GROUP, SLF4J_ARTIFACT)) {
                continue;
            }

            final Element versionElement = childElement(dependency, "version");
            if (versionElement == null) {
                appendChildElement(document, dependency, "version", TARGET_SLF4J_VERSION);
                changed = true;
                continue;
            }

            final String rawVersion = versionElement.getTextContent().trim();
            if (Objects.equals(rawVersion, TARGET_SLF4J_VERSION)) {
                continue;
            }

            if (rawVersion.startsWith("${") && rawVersion.endsWith("}")) {
                final String propertyName = rawVersion.substring(2, rawVersion.length() - 1);
                if (properties.containsKey(propertyName)) {
                    setPropertyValue(document, propertyName, TARGET_SLF4J_VERSION);
                    changed = true;
                    continue;
                }
            }

            if (rawVersion.startsWith("1.7")) {
                versionElement.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static void setPropertyValue(Document document, String propertyName, String newValue) {
        final NodeList propertySections = document.getElementsByTagNameNS("*", "properties");
        if (propertySections.getLength() == 0) {
            return;
        }
        final Node propertiesNode = propertySections.item(0);
        final NodeList children = propertiesNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && propertyName.equals(child.getLocalName())) {
                child.setTextContent(newValue);
                return;
            }
        }
    }

    private static String resolvedVersion(Element dependency, Map<String, String> properties) {
        final Element versionElement = childElement(dependency, "version");
        if (versionElement == null) {
            return null;
        }
        final String raw = versionElement.getTextContent().trim();
        if (raw.startsWith("${") && raw.endsWith("}")) {
            return properties.get(raw.substring(2, raw.length() - 1));
        }
        return raw;
    }

    private static boolean matches(Element dependency, String groupId, String artifactId) {
        return groupId.equals(textOfChild(dependency, "groupId")) && artifactId.equals(textOfChild(dependency, "artifactId"));
    }

    private static List<Element> dependencies(Document document) {
        final NodeList nodes = document.getElementsByTagNameNS("*", "dependency");
        final List<Element> dependencies = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                dependencies.add((Element) node);
            }
        }
        return dependencies;
    }

    private static String textOfChild(Element parent, String childName) {
        final Element child = childElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String childName) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static void appendChildElement(Document document, Element parent, String name, String value) {
        final Element element = document.createElementNS(parent.getNamespaceURI(), name);
        final Text text = document.createTextNode(value);
        element.appendChild(text);
        parent.appendChild(element);
    }

    private static boolean isAtLeast(String version, int major, int minor, int patch) {
        final String[] parts = version.split("\\.");
        final int vMajor = parsePart(parts, 0);
        final int vMinor = parsePart(parts, 1);
        final int vPatch = parsePart(parts, 2);

        if (vMajor != major) {
            return vMajor > major;
        }
        if (vMinor != minor) {
            return vMinor > minor;
        }
        return vPatch >= patch;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        final String cleaned = parts[index].replaceAll("[^0-9].*$", "");
        if (cleaned.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(cleaned);
    }

    private static void writeDocument(Document document, Path path) throws Exception {
        final TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        final Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
    }
}
