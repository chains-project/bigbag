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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String COMPATIBLE_SLF4J_VERSION = "2.0.16";

    public static void main(String[] args) {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        try {
            rewritePomFiles(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite Maven descriptors under " + root, e);
        }
    }

    private static void rewritePomFiles(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("pom.xml")) {
                    rewritePom(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewritePom(Path pomPath) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setNamespaceAware(false);

            Document document = factory.newDocumentBuilder().parse(Files.newInputStream(pomPath));
            document.getDocumentElement().normalize();

            boolean changed = false;
            String logbackVersion = findDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID);
            changed |= ensureSlf4jVersionCompatibility(document, logbackVersion);
            changed |= ensureLogbackCoreCompatibility(document, logbackVersion);

            if (changed) {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pomPath)));
            }
        } catch (Exception e) {
            throw new IOException("Failed to rewrite " + pomPath, e);
        }
    }

    private static boolean ensureSlf4jVersionCompatibility(Document document, String logbackVersion) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependencyNode = dependencies.item(i);
            if (dependencyNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) dependencyNode;
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String version = childText(dependency, "version");

            if (SLF4J_GROUP_ID.equals(groupId) && SLF4J_ARTIFACT_ID.equals(artifactId) && isLegacySlf4jVersion(version)) {
                setChildText(dependency, "version", COMPATIBLE_SLF4J_VERSION);
                changed = true;
            }

            if (LOGBACK_GROUP_ID.equals(groupId) && LOGBACK_ARTIFACT_ID.equals(artifactId) && isBreakingLogbackVersion(version)) {
                changed |= ensureMatchingSlf4jDependency(document);
            }
        }
        return changed;
    }

    private static boolean ensureLogbackCoreCompatibility(Document document, String logbackVersion) {
        if (!isBreakingLogbackVersion(logbackVersion)) {
            return false;
        }
        return ensureDependencyVersion(document, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID, logbackVersion);
    }

    private static boolean ensureMatchingSlf4jDependency(Document document) {
        return ensureDependencyVersion(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID, COMPATIBLE_SLF4J_VERSION);
    }

    private static boolean ensureDependencyVersion(Document document, String groupIdValue, String artifactIdValue, String targetVersion) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependencyNode = dependencies.item(i);
            if (dependencyNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) dependencyNode;
            if (groupIdValue.equals(childText(dependency, "groupId")) && artifactIdValue.equals(childText(dependency, "artifactId"))) {
                String currentVersion = childText(dependency, "version");
                if (!targetVersion.equals(currentVersion)) {
                    setChildText(dependency, "version", targetVersion);
                    return true;
                }
                return false;
            }
        }
        Element dependenciesElement = getDirectChild(document.getDocumentElement(), "dependencies");
        if (dependenciesElement == null) {
            dependenciesElement = document.createElement("dependencies");
            document.getDocumentElement().appendChild(dependenciesElement);
        }
        Element dependency = document.createElement("dependency");
        Element groupId = document.createElement("groupId");
        groupId.setTextContent(groupIdValue);
        Element artifactId = document.createElement("artifactId");
        artifactId.setTextContent(artifactIdValue);
        Element version = document.createElement("version");
        version.setTextContent(targetVersion);
        dependency.appendChild(groupId);
        dependency.appendChild(artifactId);
        dependency.appendChild(version);
        dependenciesElement.appendChild(dependency);
        return true;
    }

    private static boolean isBreakingLogbackVersion(String version) {
        return version != null && version.startsWith("1.4.");
    }

    private static boolean isLegacySlf4jVersion(String version) {
        return version != null && (version.startsWith("1.7.") || version.startsWith("1.6.") || version.startsWith("1.5."));
    }

    private static String findDependencyVersion(Document document, String groupIdValue, String artifactIdValue) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependencyNode = dependencies.item(i);
            if (dependencyNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) dependencyNode;
            if (groupIdValue.equals(childText(dependency, "groupId")) && artifactIdValue.equals(childText(dependency, "artifactId"))) {
                return childText(dependency, "version");
            }
        }
        return null;
    }

    private static Element getDirectChild(Element parent, String childName) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getParentNode() == parent) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String childText(Element parent, String childName) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getParentNode() == parent) {
                return child.getTextContent() == null ? null : child.getTextContent().trim();
            }
        }
        return null;
    }

    private static void setChildText(Element parent, String childName, String value) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getParentNode() == parent) {
                child.setTextContent(value);
                return;
            }
        }
        Element child = parent.getOwnerDocument().createElement(childName);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
