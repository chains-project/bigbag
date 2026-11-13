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
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_LOGBACK_VERSION = "1.4.5";
    private static final String TARGET_SLF4J_VERSION = "2.0.13";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        List<Path> poms = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                    .forEach(poms::add);
        }

        for (Path pom : poms) {
            if (updatePom(pom)) {
                System.out.println("Updated " + pom);
            }
        }
    }

    private static boolean updatePom(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);

        Document document;
        try (var in = Files.newInputStream(pom)) {
            document = factory.newDocumentBuilder().parse(in);
        }

        boolean changed = false;
        boolean hasLogbackClassic = hasDependency(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID);
        if (hasLogbackClassic) {
            changed |= updateSlf4jDependencies(document);
            changed |= ensureLogbackCoreDependency(document);
        }

        if (changed) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pom)));
        }

        return changed;
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (groupId.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean updateSlf4jDependencies(Document document) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            Element version = childElement(dependency, "version");
            if (version == null) {
                continue;
            }

            String value = version.getTextContent().trim();
            if (SLF4J_GROUP_ID.equals(groupId) && SLF4J_ARTIFACT_ID.equals(artifactId)) {
                if (isOldSlf4jVersion(value)) {
                    version.setTextContent(TARGET_SLF4J_VERSION);
                    changed = true;
                } else if (value.startsWith("${") && value.endsWith("}")) {
                    String propertyName = value.substring(2, value.length() - 1);
                    changed |= updatePropertyIfPresent(document, propertyName);
                }
            }

            if (LOGBACK_GROUP_ID.equals(groupId) && LOGBACK_CORE_ARTIFACT_ID.equals(artifactId) && !TARGET_LOGBACK_VERSION.equals(value)) {
                version.setTextContent(TARGET_LOGBACK_VERSION);
                changed = true;
            }
        }

        changed |= updateMatchingProperties(document);
        return changed;
    }

    private static boolean updatePropertyIfPresent(Document document, String propertyName) {
        Element property = childElement(childElement(document.getDocumentElement(), "properties"), propertyName);
        if (property != null && isOldSlf4jVersion(property.getTextContent().trim())) {
            property.setTextContent(TARGET_SLF4J_VERSION);
            return true;
        }
        return false;
    }

    private static boolean updateMatchingProperties(Document document) {
        Element properties = childElement(document.getDocumentElement(), "properties");
        if (properties == null) {
            return false;
        }

        boolean changed = false;
        NodeList children = properties.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element property = (Element) node;
            String name = property.getTagName();
            String value = property.getTextContent().trim();
            if ((name.toLowerCase().contains("slf4j") || value.startsWith("1.7.")) && isOldSlf4jVersion(value)) {
                property.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            } else if (name.toLowerCase().contains("logback") && value.startsWith("1.2.")) {
                property.setTextContent(TARGET_LOGBACK_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean ensureLogbackCoreDependency(Document document) {
        if (hasDependency(document, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID)) {
            return false;
        }

        Element dependencies = childElement(document.getDocumentElement(), "dependencies");
        if (dependencies == null) {
            return false;
        }

        Element dependency = document.createElement("dependency");
        appendTextElement(document, dependency, "groupId", LOGBACK_GROUP_ID);
        appendTextElement(document, dependency, "artifactId", LOGBACK_CORE_ARTIFACT_ID);
        appendTextElement(document, dependency, "version", TARGET_LOGBACK_VERSION);
        dependencies.appendChild(document.createTextNode("\n        "));
        dependencies.appendChild(dependency);
        dependencies.appendChild(document.createTextNode("\n    "));
        return true;
    }

    private static void appendTextElement(Document document, Element parent, String name, String value) {
        Element element = document.createElement(name);
        element.setTextContent(value);
        parent.appendChild(document.createTextNode("\n            "));
        parent.appendChild(element);
        parent.appendChild(document.createTextNode("\n        "));
    }

    private static boolean isOldSlf4jVersion(String version) {
        return version.startsWith("1.7.") || version.equals("1.7.36");
    }

    private static Element childElement(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && name.equals(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String childText(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }
}
