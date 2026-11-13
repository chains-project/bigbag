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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.7";
    private static final String TARGET_LOGBACK_CORE_VERSION = "1.4.3";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a source directory path");
        }

        Path sourceDir = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> pomFiles = findPomFiles(sourceDir);

        for (Path pomFile : pomFiles) {
            transformPom(pomFile);
        }
    }

    private static List<Path> findPomFiles(Path sourceDir) throws IOException {
        List<Path> poms = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("pom.xml")) {
                    poms.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return poms;
    }

    private static void transformPom(Path pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);

        Document document = factory.newDocumentBuilder().parse(pomFile.toFile());
        boolean hasLogback143 = hasDependencyVersion(document, LOGBACK_GROUP, LOGBACK_ARTIFACT, version -> version.startsWith("1.4."));
        if (!hasLogback143) {
            return;
        }

        boolean changed = updateDependencyVersion(document, SLF4J_GROUP, SLF4J_ARTIFACT, TARGET_SLF4J_VERSION);
        changed |= updateDependencyVersion(document, LOGBACK_GROUP, "logback-core", TARGET_LOGBACK_CORE_VERSION);
        changed |= updateProperty(document, "slf4j.version", TARGET_SLF4J_VERSION);
        changed |= updateProperty(document, "logback.version", TARGET_LOGBACK_CORE_VERSION);
        changed |= ensureDependencyVersion(document, LOGBACK_GROUP, "logback-core", TARGET_LOGBACK_CORE_VERSION);

        if (changed) {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.transform(new DOMSource(document), new StreamResult(pomFile.toFile()));
        }
    }

    private static boolean hasDependencyVersion(Document document, String groupId, String artifactId, VersionPredicate predicate) {
        for (Element dependency : dependencyElements(document)) {
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                String version = textOfChild(dependency, "version");
                if (version != null && predicate.test(version.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        boolean changed = false;
        for (Element dependency : dependencyElements(document)) {
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                changed |= setChildText(document, dependency, "version", newVersion);
            }
        }
        return changed;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        for (Element dependency : dependencyElements(document)) {
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                return false;
            }
        }

        NodeList dependencyParents = document.getElementsByTagName("dependencies");
        if (dependencyParents.getLength() == 0) {
            return false;
        }

        Element dependencies = (Element) dependencyParents.item(0);
        Element dependency = document.createElement("dependency");
        Element group = document.createElement("groupId");
        group.setTextContent(groupId);
        Element artifact = document.createElement("artifactId");
        artifact.setTextContent(artifactId);
        Element version = document.createElement("version");
        version.setTextContent(newVersion);
        dependency.appendChild(group);
        dependency.appendChild(artifact);
        dependency.appendChild(version);
        dependencies.appendChild(dependency);
        return true;
    }

    private static boolean updateProperty(Document document, String propertyName, String newValue) {
        NodeList propertiesList = document.getElementsByTagName("properties");
        boolean changed = false;
        for (int i = 0; i < propertiesList.getLength(); i++) {
            Node node = propertiesList.item(i);
            if (node instanceof Element properties) {
                NodeList children = properties.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child instanceof Element element && propertyName.equals(element.getTagName())) {
                        if (!newValue.equals(element.getTextContent().trim())) {
                            element.setTextContent(newValue);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static List<Element> dependencyElements(Document document) {
        List<Element> dependencies = new ArrayList<>();
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node node = dependencyNodes.item(i);
            if (node instanceof Element element) {
                dependencies.add(element);
            }
        }
        return dependencies;
    }

    private static String textOfChild(Element parent, String childTag) {
        NodeList children = parent.getElementsByTagName(childTag);
        if (children.getLength() == 0) {
            return null;
        }
        return children.item(0).getTextContent();
    }

    private static boolean setChildText(Document document, Element parent, String childTag, String newValue) {
        NodeList children = parent.getElementsByTagName(childTag);
        if (children.getLength() == 0) {
            Element child = document.createElement(childTag);
            child.setTextContent(newValue);
            parent.appendChild(child);
            return true;
        }

        Element child = (Element) children.item(0);
        if (!newValue.equals(child.getTextContent().trim())) {
            child.setTextContent(newValue);
            return true;
        }
        return false;
    }

    @FunctionalInterface
    private interface VersionPredicate {
        boolean test(String version);
    }
}
