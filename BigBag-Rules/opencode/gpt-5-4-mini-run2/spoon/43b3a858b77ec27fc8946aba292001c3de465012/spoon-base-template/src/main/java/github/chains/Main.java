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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.namespace.NamespaceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class Main {

    private static final String MAVEN_NS = "http://maven.apache.org/POM/4.0.0";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.13";
    private static final String OLD_SLF4J_VERSION_PREFIX = "1.7.";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a project directory path");
        }

        Path projectDir = Paths.get(args[0]).toAbsolutePath().normalize();
        List<Path> pomFiles = new ArrayList<>();

        try (var stream = Files.walk(projectDir)) {
            stream.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                    .forEach(pomFiles::add);
        }

        for (Path pom : pomFiles) {
            updatePomIfNeeded(pom);
        }
    }

    private static void updatePomIfNeeded(Path pomPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            Document document = factory.newDocumentBuilder().parse(pomPath.toFile());
            document.getDocumentElement().normalize();

            boolean hasLogback14 = containsDependencyVersionPrefix(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID, "1.4");
            if (!hasLogback14) {
                return;
            }

            boolean changed = false;
            changed |= updateSlf4jDependencyVersions(document, "/m:project/m:dependencies/m:dependency");
            changed |= updateSlf4jDependencyVersions(document, "/m:project/m:dependencyManagement/m:dependencies/m:dependency");

            if (changed) {
                writeDocument(document, pomPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update " + pomPath, e);
        }
    }

    private static boolean containsDependencyVersionPrefix(Document document, String groupId, String artifactId, String prefix) {
        return findDependencyElements(document).stream().anyMatch(dep -> groupId.equals(childText(dep, "groupId"))
                && artifactId.equals(childText(dep, "artifactId"))
                && childText(dep, "version") != null
                && childText(dep, "version").startsWith(prefix));
    }

    private static boolean updateSlf4jDependencyVersions(Document document, String xpathLikePath) throws XPathExpressionException {
        boolean changed = false;
        for (Element dependency : findDependencyContainers(document, xpathLikePath)) {
            if (SLF4J_GROUP_ID.equals(childText(dependency, "groupId"))
                    && SLF4J_ARTIFACT_ID.equals(childText(dependency, "artifactId"))) {
                String currentVersion = childText(dependency, "version");
                if (currentVersion != null && currentVersion.startsWith(OLD_SLF4J_VERSION_PREFIX)) {
                    setChildText(document, dependency, "version", TARGET_SLF4J_VERSION);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static List<Element> findDependencyElements(Document document) {
        try {
            XPath xpath = newXPath();
            NodeList nodes = (NodeList) xpath.evaluate("//m:dependency", document, XPathConstants.NODESET);
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                elements.add((Element) nodes.item(i));
            }
            return elements;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Element> findDependencyContainers(Document document, String path) throws XPathExpressionException {
        XPath xpath = newXPath();
        NodeList nodes = (NodeList) xpath.evaluate(path, document, XPathConstants.NODESET);
        List<Element> results = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            results.add((Element) nodes.item(i));
        }
        return results;
    }

    private static XPath newXPath() {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                return "m".equals(prefix) ? MAVEN_NS : XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return MAVEN_NS.equals(namespaceURI) ? "m" : null;
            }

            @Override
            public Iterator<String> getPrefixes(String namespaceURI) {
                return Objects.equals(namespaceURI, MAVEN_NS)
                        ? List.of("m").iterator()
                        : List.<String>of().iterator();
            }
        });
        return xpath;
    }

    private static String childText(Element element, String childName) {
        Element child = directChild(element, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void setChildText(Document document, Element parent, String childName, String value) {
        Element child = directChild(parent, childName);
        if (child == null) {
            child = document.createElementNS(MAVEN_NS, childName);
            parent.appendChild(document.createTextNode("\n        "));
            parent.appendChild(child);
            parent.appendChild(document.createTextNode("\n    "));
        }
        child.setTextContent(value);
    }

    private static Element directChild(Element parent, String childName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && childName.equals(element.getLocalName())) {
                return element;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static void writeDocument(Document document, Path pomPath) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
    }
}
