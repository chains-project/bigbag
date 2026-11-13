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
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.13";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        List<Path> poms = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName() != null && "pom.xml".equals(path.getFileName().toString()))
                  .forEach(poms::add);
        }

        for (Path pom : poms) {
            rewritePomIfNeeded(pom);
        }
    }

    private static void rewritePomIfNeeded(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);

        Document document;
        try (InputStream in = Files.newInputStream(pom)) {
            document = factory.newDocumentBuilder().parse(in);
        }

        boolean hasIncompatibleLogback = hasLogbackClassic14Plus(document);
        if (!hasIncompatibleLogback) {
            return;
        }

        boolean changed = updateSlf4jApiDependencies(document);
        if (changed) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            try (OutputStream out = Files.newOutputStream(pom)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        }
    }

    private static boolean hasLogbackClassic14Plus(Document document) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String version = childText(dependency, "version");
            if (LOGBACK_GROUP.equals(groupId) && LOGBACK_ARTIFACT.equals(artifactId) && isAtLeast14(version)) {
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
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if (!SLF4J_GROUP.equals(groupId) || !SLF4J_ARTIFACT.equals(artifactId)) {
                continue;
            }

            Element version = childElement(dependency, "version");
            if (version == null) {
                version = document.createElement("version");
                version.setTextContent(TARGET_SLF4J_VERSION);
                dependency.appendChild(version);
                changed = true;
                continue;
            }

            String versionText = version.getTextContent() == null ? "" : version.getTextContent().trim();
            if (versionText.startsWith("${") && versionText.endsWith("}")) {
                String propertyName = versionText.substring(2, versionText.length() - 1);
                if (updateProperty(document, propertyName, TARGET_SLF4J_VERSION)) {
                    changed = true;
                }
            } else if (!TARGET_SLF4J_VERSION.equals(versionText)) {
                version.setTextContent(TARGET_SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateProperty(Document document, String propertyName, String value) {
        NodeList propertiesList = document.getElementsByTagName("properties");
        for (int i = 0; i < propertiesList.getLength(); i++) {
            Node node = propertiesList.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element properties = (Element) node;
            Element property = childElement(properties, propertyName);
            if (property != null) {
                property.setTextContent(value);
                return true;
            }
        }
        return false;
    }

    private static String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    private static boolean isAtLeast14(String version) {
        if (version == null) {
            return false;
        }
        String trimmed = version.trim();
        if (trimmed.startsWith("${")) {
            return false;
        }
        String[] parts = trimmed.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0].replaceAll("[^0-9].*$", ""));
            int minor = Integer.parseInt(parts[1].replaceAll("[^0-9].*$", ""));
            return major > 1 || (major == 1 && minor >= 4);
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
