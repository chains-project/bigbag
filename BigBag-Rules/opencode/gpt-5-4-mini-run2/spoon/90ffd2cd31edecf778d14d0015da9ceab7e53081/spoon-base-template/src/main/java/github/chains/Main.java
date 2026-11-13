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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC = "logback-classic";
    private static final String LOGBACK_CORE = "logback-core";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_API = "slf4j-api";

    private static final String LOGBACK_VERSION = "1.4.0";
    private static final String SLF4J_VERSION = "2.0.0";

    public static void main(String[] args) {
        Path input = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path root = input.toAbsolutePath().normalize();

        try (Stream<Path> paths = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            paths.filter(Main::isPomFile)
                    .forEach(Main::updatePomSafely);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process input directory: " + input, e);
        }
    }

    private static boolean isPomFile(Path path) {
        return path.getFileName() != null && "pom.xml".equals(path.getFileName().toString());
    }

    private static void updatePomSafely(Path pom) {
        try {
            if (updatePom(pom.toFile())) {
                System.out.println("Updated " + pom);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update " + pom, e);
        }
    }

    private static boolean updatePom(File pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);

        Document document = factory.newDocumentBuilder().parse(pomFile);
        NodeList dependencies = document.getElementsByTagName("dependency");

        boolean breakingLogbackFound = false;
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element dependency = (Element) node;
            if (LOGBACK_GROUP.equals(childText(dependency, "groupId"))
                    && LOGBACK_CLASSIC.equals(childText(dependency, "artifactId"))
                    && childText(dependency, "version") != null
                    && childText(dependency, "version").startsWith("1.4.")) {
                breakingLogbackFound = true;
                break;
            }
        }

        if (!breakingLogbackFound) {
            return false;
        }

        boolean changed = false;
        changed |= ensureDependencyVersion(document, dependencies, SLF4J_GROUP, SLF4J_API, SLF4J_VERSION, false);
        changed |= ensureDependencyVersion(document, dependencies, LOGBACK_GROUP, LOGBACK_CORE, LOGBACK_VERSION, true);

        if (changed) {
            writeDocument(document, pomFile);
        }

        return changed;
    }

    private static boolean ensureDependencyVersion(Document document, NodeList dependencies, String groupId, String artifactId, String version, boolean createIfMissing) {
        boolean changed = false;
        boolean found = false;
        Node dependenciesParent = null;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            if (dependenciesParent == null) {
                dependenciesParent = node.getParentNode();
            }

            Element dependency = (Element) node;
            if (!groupId.equals(childText(dependency, "groupId")) || !artifactId.equals(childText(dependency, "artifactId"))) {
                continue;
            }

            found = true;

            String current = childText(dependency, "version");
            if (!version.equals(current)) {
                setChildText(document, dependency, "version", version);
                changed = true;
            }
        }

        if (!found && createIfMissing && dependenciesParent != null) {
            Element dependency = createDependency(document, groupId, artifactId, version);
            dependenciesParent.appendChild(dependency);
            changed = true;
        }

        return changed;
    }

    private static Element createDependency(Document document, String groupId, String artifactId, String version) {
        Element dependency = document.createElement("dependency");
        Element group = document.createElement("groupId");
        group.setTextContent(groupId);
        Element artifact = document.createElement("artifactId");
        artifact.setTextContent(artifactId);
        Element dependencyVersion = document.createElement("version");
        dependencyVersion.setTextContent(version);
        dependency.appendChild(group);
        dependency.appendChild(artifact);
        dependency.appendChild(dependencyVersion);
        return dependency;
    }

    private static String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static void setChildText(Document document, Element parent, String tagName, String text) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            nodes.item(0).setTextContent(text);
            return;
        }

        Element child = document.createElement(tagName);
        child.setTextContent(text);
        parent.appendChild(child);
    }

    private static void writeDocument(Document document, File pomFile) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomFile));
    }
}
