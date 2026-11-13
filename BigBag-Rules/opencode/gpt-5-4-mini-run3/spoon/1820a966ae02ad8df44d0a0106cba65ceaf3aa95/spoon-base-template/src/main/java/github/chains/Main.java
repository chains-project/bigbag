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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT = "logback-core";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.13";
    private static final String TARGET_LOGBACK_VERSION = "1.4.4";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <project-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Project root does not exist: " + root);
        }

        List<Path> poms = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml")).forEach(poms::add);
        }

        boolean changed = false;
        for (Path pom : poms) {
            changed |= alignSlf4jWithLogback(pom);
        }

        if (!changed) {
            System.out.println("No dependency alignment changes were needed.");
        }
    }

    private static boolean alignSlf4jWithLogback(Path pomPath) throws Exception {
        Document document = parseXml(pomPath);

        boolean hasLogback = false;
        boolean changed = false;

        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");

            if (LOGBACK_GROUP.equals(groupId) && LOGBACK_ARTIFACT.equals(artifactId)) {
                hasLogback = true;
            }

            if (SLF4J_GROUP.equals(groupId) && SLF4J_ARTIFACT.equals(artifactId)) {
                String version = childText(dependency, "version");
                if (!TARGET_SLF4J_VERSION.equals(version)) {
                    setChildText(document, dependency, "version", TARGET_SLF4J_VERSION);
                    changed = true;
                }
            }

            if (LOGBACK_GROUP.equals(groupId) && LOGBACK_CORE_ARTIFACT.equals(artifactId)) {
                String version = childText(dependency, "version");
                if (!TARGET_LOGBACK_VERSION.equals(version)) {
                    setChildText(document, dependency, "version", TARGET_LOGBACK_VERSION);
                    changed = true;
                }
            }
        }

        if (hasLogback && !hasSlf4jDependency(document)) {
            Element dependencies = firstChildElement(document.getDocumentElement(), "dependencies");
            if (dependencies != null) {
                Element dependency = document.createElement("dependency");
                appendChildWithText(document, dependency, "groupId", SLF4J_GROUP);
                appendChildWithText(document, dependency, "artifactId", SLF4J_ARTIFACT);
                appendChildWithText(document, dependency, "version", TARGET_SLF4J_VERSION);
                dependencies.appendChild(document.createTextNode("\n        "));
                dependencies.appendChild(dependency);
                dependencies.appendChild(document.createTextNode("\n    "));
                changed = true;
            }
        }

        if (hasLogback && !hasLogbackCoreDependency(document)) {
            Element dependencies = firstChildElement(document.getDocumentElement(), "dependencies");
            if (dependencies != null) {
                Element dependency = document.createElement("dependency");
                appendChildWithText(document, dependency, "groupId", LOGBACK_GROUP);
                appendChildWithText(document, dependency, "artifactId", LOGBACK_CORE_ARTIFACT);
                appendChildWithText(document, dependency, "version", TARGET_LOGBACK_VERSION);
                dependencies.appendChild(document.createTextNode("\n        "));
                dependencies.appendChild(dependency);
                dependencies.appendChild(document.createTextNode("\n    "));
                changed = true;
            }
        }

        if (changed) {
            writeXml(document, pomPath);
            System.out.println("Aligned SLF4J dependency in " + pomPath);
        }
        return changed;
    }

    private static boolean hasSlf4jDependency(Document document) {
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            if (SLF4J_GROUP.equals(childText(dependency, "groupId"))
                    && SLF4J_ARTIFACT.equals(childText(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLogbackCoreDependency(Document document) {
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            if (LOGBACK_GROUP.equals(childText(dependency, "groupId"))
                    && LOGBACK_CORE_ARTIFACT.equals(childText(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        try (InputStream inputStream = Files.newInputStream(path)) {
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private static void writeXml(Document document, Path path) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            transformer.transform(new DOMSource(document), new StreamResult(outputStream));
        }
    }

    private static String childText(Element parent, String childName) {
        Element child = firstChildElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void setChildText(Document document, Element parent, String childName, String value) {
        Element child = firstChildElement(parent, childName);
        if (child == null) {
            appendChildWithText(document, parent, childName, value);
        } else {
            child.setTextContent(value);
        }
    }

    private static void appendChildWithText(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(value);
        parent.appendChild(document.createTextNode("\n        "));
        parent.appendChild(child);
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}
