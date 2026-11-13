package github.chains;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.13";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <project-root>");
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        if (containsLogbackClassicReferences(root) || hasLogbackClassicDependency(root)) {
            updatePomFiles(root);
        }
    }

    private static boolean containsLogbackClassicReferences(Path root) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);

        List<Path> sourceRoots = List.of(root.resolve("src/main/java"), root.resolve("src/test/java"));
        boolean added = false;
        for (Path sourceRoot : sourceRoots) {
            if (Files.isDirectory(sourceRoot)) {
                launcher.addInputResource(sourceRoot.toString());
                added = true;
            }
        }
        if (!added) {
            return false;
        }

        launcher.buildModel();
        return launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).stream()
                .map(CtTypeReference::getQualifiedName)
                .anyMatch(qn -> qn.equals("ch.qos.logback.classic.Logger")
                        || qn.equals("ch.qos.logback.classic.Level")
                        || qn.equals("ch.qos.logback.classic.spi.ILoggingEvent")
                        || qn.equals("ch.qos.logback.core.Appender"));
    }

    private static boolean hasLogbackClassicDependency(Path root) throws Exception {
        return Files.walk(root)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .anyMatch(path -> {
                    try {
                        Document document = parseXml(path);
                        return findMatchingDependency(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID) != null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static void updatePomFiles(Path root) throws Exception {
        List<Path> poms = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> path.getFileName().toString().equals("pom.xml")).forEach(poms::add);
        }
        poms.sort(Comparator.naturalOrder());

        for (Path pom : poms) {
            Document document = parseXml(pom);
            if (updateSlf4jDependency(document)) {
                writeXml(document, pom);
            }
        }
    }

    private static boolean updateSlf4jDependency(Document document) {
        Element dependency = findMatchingDependency(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID);
        if (dependency == null) {
            return false;
        }

        Element versionElement = childElement(dependency, "version");
        if (versionElement == null) {
            versionElement = document.createElement("version");
            versionElement.setTextContent(TARGET_SLF4J_VERSION);
            dependency.appendChild(versionElement);
            return true;
        }

        String versionText = versionElement.getTextContent().trim();
        if (versionText.equals(TARGET_SLF4J_VERSION)) {
            return false;
        }

        if (versionText.startsWith("${") && versionText.endsWith("}")) {
            String propertyName = versionText.substring(2, versionText.length() - 1);
            Element property = childElement(document.getDocumentElement(), propertyName);
            if (property != null) {
                property.setTextContent(TARGET_SLF4J_VERSION);
                return true;
            }
        }

        versionElement.setTextContent(TARGET_SLF4J_VERSION);
        return true;
    }

    private static Element findMatchingDependency(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }
            Element groupIdElement = childElement(dependency, "groupId");
            Element artifactIdElement = childElement(dependency, "artifactId");
            if (groupIdElement == null || artifactIdElement == null) {
                continue;
            }
            if (groupId.equals(groupIdElement.getTextContent().trim()) && artifactId.equals(artifactIdElement.getTextContent().trim())) {
                return dependency;
            }
        }
        return null;
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static Document parseXml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setIgnoringComments(false);
        factory.setIgnoringElementContentWhitespace(false);
        var builder = factory.newDocumentBuilder();
        try (var in = Files.newInputStream(path)) {
            return builder.parse(in);
        }
    }

    private static void writeXml(Document document, Path path) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        try (var out = Files.newOutputStream(path)) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }
}
