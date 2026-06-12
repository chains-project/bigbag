package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String FIXED_SLF4J_VERSION = "2.0.0";
    private static final Pattern PROPERTY_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        List<Path> poms = collectFiles(root, "pom.xml");
        for (Path pom : poms) {
            updatePomIfNeeded(pom);
        }
    }

    private static List<Path> collectFiles(Path root, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> result = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(path -> Objects.equals(path.getFileName().toString(), fileName))
                    .sorted(Comparator.naturalOrder())
                    .forEach(result::add);
            return result;
        }
    }

    private static void updatePomIfNeeded(Path pomPath) throws Exception {
        Document document = parseXml(pomPath);
        if (!hasDependency(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID)) {
            return;
        }

        boolean changed = false;
        changed |= updateSlf4jDependencies(document);
        changed |= updateSlf4jProperties(document);

        if (changed) {
            writeXml(document, pomPath);
        }
    }

    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static void writeXml(Document document, Path path) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagNameNS("*", "dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependencies(Document document) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagNameNS("*", "dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (SLF4J_GROUP_ID.equals(textOfChild(dependency, "groupId"))
                    && SLF4J_ARTIFACT_ID.equals(textOfChild(dependency, "artifactId"))) {
                Element version = childElement(dependency, "version");
                if (version != null) {
                    String versionText = version.getTextContent();
                    if (needsUpgrade(versionText)) {
                        if (isPropertyReference(versionText)) {
                            String propertyName = propertyReferenceName(versionText);
                            changed |= updateProperty(document, propertyName, FIXED_SLF4J_VERSION);
                        } else {
                            version.setTextContent(FIXED_SLF4J_VERSION);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean updateSlf4jProperties(Document document) {
        Element properties = childElement(document.getDocumentElement(), "properties");
        if (properties == null) {
            return false;
        }

        boolean changed = false;
        NodeList children = properties.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element property = (Element) node;
                String name = property.getLocalName() != null ? property.getLocalName() : property.getTagName();
                if (name.toLowerCase(Locale.ROOT).contains("slf4j") && name.toLowerCase(Locale.ROOT).contains("version")) {
                    changed |= updateProperty(document, name, FIXED_SLF4J_VERSION);
                }
            }
        }
        return changed;
    }

    private static boolean updateProperty(Document document, String propertyName, String newValue) {
        if (propertyName == null || propertyName.isEmpty()) {
            return false;
        }
        Element properties = childElement(document.getDocumentElement(), "properties");
        if (properties == null) {
            return false;
        }
        Element property = childElement(properties, propertyName);
        if (property != null && !newValue.equals(property.getTextContent().trim())) {
            property.setTextContent(newValue);
            return true;
        }
        return false;
    }

    private static boolean needsUpgrade(String versionText) {
        if (versionText == null) {
            return false;
        }
        String normalized = versionText.trim();
        if (isPropertyReference(normalized)) {
            return true;
        }
        return normalized.startsWith("1.7.") || normalized.equals("1.7") || normalized.startsWith("1.");
    }

    private static boolean isPropertyReference(String text) {
        return text != null && PROPERTY_REFERENCE.matcher(text.trim()).matches();
    }

    private static String propertyReferenceName(String text) {
        Matcher matcher = PROPERTY_REFERENCE.matcher(text.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static Element childElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
                if (localName.equals(name)) {
                    return element;
                }
            }
        }
        return null;
    }

    private static String textOfChild(Element parent, String localName) {
        Element child = childElement(parent, localName);
        return child == null ? null : child.getTextContent().trim();
    }
}
