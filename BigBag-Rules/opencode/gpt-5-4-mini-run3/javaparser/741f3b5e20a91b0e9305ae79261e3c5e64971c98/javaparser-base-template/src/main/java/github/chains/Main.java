package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String JACKSON_PREFIX = "com.fasterxml.jackson.";
    private static final String DATABIND = "jackson-databind";
    private static final String JCENTER_URL = "https://jcenter.bintray.com";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Input path does not exist: " + root);
        }

        List<Path> poms = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml")).forEach(poms::add);
        }

        for (Path pom : poms) {
            rewritePom(pom);
        }
    }

    private static void rewritePom(Path pom) throws Exception {
        Document document = parseXml(pom);
        String databindVersion = findDependencyVersion(document, DATABIND);
        if (databindVersion == null || databindVersion.isBlank()) {
            return;
        }

        boolean changed = false;
        changed |= alignJacksonDependencies(document, normalizeSiblingVersion(databindVersion));
        changed |= removeJcenterRepository(document);

        if (changed) {
            writeXml(document, pom);
        }
    }

    private static Document parseXml(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        try (InputStream in = Files.newInputStream(pom)) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    private static void writeXml(Document document, Path pom) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        try (var out = Files.newOutputStream(pom)) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }

    private static String findDependencyVersion(Document document, String artifactId) {
        for (Element dependency : allElements(document, "dependency")) {
            String currentArtifact = childText(dependency, "artifactId");
            if (artifactId.equals(currentArtifact)) {
                String version = childText(dependency, "version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        }
        return null;
    }

    private static boolean alignJacksonDependencies(Document document, String version) {
        boolean changed = false;
        for (Element dependency : allElements(document, "dependency")) {
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (!groupId.startsWith(JACKSON_PREFIX)) {
                continue;
            }
            changed |= setChildText(document, dependency, "version", version);
        }
        return changed;
    }

    private static boolean removeJcenterRepository(Document document) {
        boolean changed = false;
        for (Element repository : allElements(document, "repository")) {
            String url = childText(repository, "url");
            if (url != null && url.contains(JCENTER_URL)) {
                Node parent = repository.getParentNode();
                if (parent != null) {
                    parent.removeChild(repository);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean setChildText(Document document, Element parent, String childName, String value) {
        Element child = childElement(parent, childName);
        if (child == null) {
            child = document.createElement(childName);
            parent.appendChild(document.createTextNode("\n            "));
            parent.appendChild(child);
            parent.appendChild(document.createTextNode("\n        "));
        }
        String current = child.getTextContent();
        if (current == null || !current.trim().equals(value)) {
            child.setTextContent(value);
            return true;
        }
        return false;
    }

    private static List<Element> allElements(Document document, String tagName) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static Element childElement(Element parent, String childName) {
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

    private static String normalizeSiblingVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length > 3 && isDigits(parts[0]) && isDigits(parts[1]) && isDigits(parts[2])) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return version;
    }

    private static boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
