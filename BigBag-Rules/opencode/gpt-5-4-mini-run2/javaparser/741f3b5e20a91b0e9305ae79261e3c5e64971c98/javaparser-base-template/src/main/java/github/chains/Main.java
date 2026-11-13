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
import java.util.Comparator;
import java.util.stream.Collectors;

public class Main {

    private static final String JACKSON_GROUP = "com.fasterxml.jackson.core";
    private static final String JACKSON_DATABIND = "jackson-databind";
    private static final String JACKSON_CORE = "jackson-core";
    private static final String JACKSON_DATATYPE_JSR310 = "jackson-datatype-jsr310";
    private static final String JACKSON_ANNOTATIONS = "jackson-annotations";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist or is not a directory: " + root);
        }

        try (var paths = Files.walk(root)) {
            for (Path pom : paths.filter(p -> p.getFileName().toString().equals("pom.xml")).sorted(Comparator.naturalOrder()).collect(Collectors.toList())) {
                transformPom(pom);
            }
        }
    }

    private static void transformPom(Path pomPath) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Files.newInputStream(pomPath));
            document.getDocumentElement().normalize();

            String jacksonVersion = findDependencyVersion(document, JACKSON_GROUP, JACKSON_DATABIND);
            boolean changed = false;
            if (jacksonVersion != null) {
                String compatibleVersion = normalizeJacksonVersion(jacksonVersion);
                changed |= alignJacksonDependencyVersion(document, JACKSON_CORE, compatibleVersion);
                changed |= alignJacksonDependencyVersion(document, JACKSON_DATATYPE_JSR310, compatibleVersion);
                changed |= ensureJacksonDependency(document, JACKSON_ANNOTATIONS, compatibleVersion);
            }
            changed |= removeDeadJCenterRepository(document);

            if (changed) {
                writeDocument(document, pomPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + pomPath, e);
        }
    }

    private static boolean alignJacksonDependencyVersion(Document document, String artifactId, String version) {
        boolean changed = false;
        for (Element dependency : dependencyElements(document)) {
            if (JACKSON_GROUP.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                changed |= setChildText(dependency, "version", version);
            }
        }
        return changed;
    }

    private static boolean ensureJacksonDependency(Document document, String artifactId, String version) {
        for (Element dependency : dependencyElements(document)) {
            if (JACKSON_GROUP.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                return setChildText(dependency, "version", version);
            }
        }

        Element dependencies = firstElementByTagName(document, "dependencies");
        if (dependencies == null) {
            return false;
        }

        Element dependency = document.createElement("dependency");
        appendTextChild(document, dependency, "groupId", JACKSON_GROUP);
        appendTextChild(document, dependency, "artifactId", artifactId);
        appendTextChild(document, dependency, "version", version);
        dependencies.appendChild(dependency);
        return true;
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        for (Element dependency : dependencyElements(document)) {
            if (groupId.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                return childText(dependency, "version");
            }
        }
        return null;
    }

    private static String normalizeJacksonVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length == 4 && "1".equals(parts[3])) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return version;
    }

    private static boolean removeDeadJCenterRepository(Document document) {
        boolean changed = false;
        NodeList repositories = document.getElementsByTagName("repository");
        for (int i = repositories.getLength() - 1; i >= 0; i--) {
            Node node = repositories.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element repository = (Element) node;
            String id = childText(repository, "id");
            String url = childText(repository, "url");
            if ((id != null && id.toLowerCase().contains("jcenter")) || (url != null && url.contains("jcenter.bintray.com"))) {
                Node parent = repository.getParentNode();
                if (parent != null) {
                    parent.removeChild(repository);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static java.util.List<Element> dependencyElements(Document document) {
        NodeList nodes = document.getElementsByTagName("dependency");
        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(nodes::item)
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .toList();
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        Node node = children.item(0);
        return node == null ? null : node.getTextContent().trim();
    }

    private static Element firstElementByTagName(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node instanceof Element ? (Element) node : null;
    }

    private static void appendTextChild(Document document, Element parent, String tagName, String value) {
        Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static boolean setChildText(Element parent, String tagName, String value) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            Node node = children.item(0);
            if (node != null && !value.equals(node.getTextContent().trim())) {
                node.setTextContent(value);
                return true;
            }
            return false;
        }
        Element child = parent.getOwnerDocument().createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
        return true;
    }

    private static void writeDocument(Document document, Path pomPath) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pomPath)));
    }
}
