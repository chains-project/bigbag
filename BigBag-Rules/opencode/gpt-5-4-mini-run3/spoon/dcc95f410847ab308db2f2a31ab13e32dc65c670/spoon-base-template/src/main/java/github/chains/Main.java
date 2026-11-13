package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import spoon.Launcher;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String LOGBACK_CLASSIC_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String UPDATED_SLF4J_VERSION = "2.0.13";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <project-root> [output-root]");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputRoot;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(inputRoot.toString());
        launcher.buildModel();

        List<Path> pomFiles = new ArrayList<>();
        try (var paths = Files.walk(inputRoot)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .forEach(pomFiles::add);
        }

        for (Path pomFile : pomFiles) {
            updatePom(pomFile, outputRoot.equals(inputRoot) ? pomFile : outputRoot.resolve(inputRoot.relativize(pomFile)));
        }
    }

    private static void updatePom(Path sourcePom, Path targetPom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(sourcePom.toFile());
        document.getDocumentElement().normalize();

        Element logbackClassic = findDependencyElement(document, LOGBACK_CLASSIC_GROUP_ID, LOGBACK_CLASSIC_ARTIFACT_ID);
        if (logbackClassic == null) {
            if (!sourcePom.equals(targetPom)) {
                Files.createDirectories(targetPom.getParent());
                Files.copy(sourcePom, targetPom, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }

        String logbackVersion = textOfChild(logbackClassic, "version");
        boolean changed = false;
        changed |= updateDependencyVersion(document, SLF4J_GROUP_ID, SLF4J_ARTIFACT_ID, UPDATED_SLF4J_VERSION);
        changed |= ensureDependencyVersion(document, LOGBACK_CLASSIC_GROUP_ID, "logback-core", logbackVersion);
        changed |= ensureDependencyVersion(document, LOGBACK_CLASSIC_GROUP_ID, "logback-access", logbackVersion);
        if (!changed) {
            if (!sourcePom.equals(targetPom)) {
                Files.createDirectories(targetPom.getParent());
                Files.copy(sourcePom, targetPom, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }

        if (targetPom.getParent() != null) {
            Files.createDirectories(targetPom.getParent());
        }
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.transform(new DOMSource(document), new StreamResult(targetPom.toFile()));
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        Element dependency = findDependencyElement(document, groupId, artifactId);
        if (dependency == null) {
            return false;
        }

        Element version = childElement(dependency, "version");
        if (version == null) {
            version = document.createElement("version");
            dependency.appendChild(version);
        }

        if (newVersion.equals(version.getTextContent().trim())) {
            return false;
        }

        version.setTextContent(newVersion);
        return true;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String newVersion) {
        Element dependency = findDependencyElement(document, groupId, artifactId);
        if (dependency == null) {
            dependency = appendDependency(document, groupId, artifactId, newVersion);
            return true;
        }

        Element version = childElement(dependency, "version");
        if (version == null) {
            version = document.createElement("version");
            dependency.appendChild(version);
        }

        if (newVersion.equals(version.getTextContent().trim())) {
            return false;
        }

        version.setTextContent(newVersion);
        return true;
    }

    private static Element appendDependency(Document document, String groupId, String artifactId, String version) {
        Element dependencies = firstChildElement(document.getDocumentElement(), "dependencies");
        if (dependencies == null) {
            dependencies = document.createElement("dependencies");
            document.getDocumentElement().appendChild(dependencies);
        }

        Element dependency = document.createElement("dependency");
        appendTextElement(document, dependency, "groupId", groupId);
        appendTextElement(document, dependency, "artifactId", artifactId);
        appendTextElement(document, dependency, "version", version);
        dependencies.appendChild(dependency);
        return dependency;
    }

    private static Element findDependencyElement(Document document, String groupId, String artifactId) {
        NodeList nodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dependency = (Element) node;
            String currentGroupId = textOfChild(dependency, "groupId");
            String currentArtifactId = textOfChild(dependency, "artifactId");
            if (groupId.equals(currentGroupId) && artifactId.equals(currentArtifactId)) {
                return dependency;
            }
        }
        return null;
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void appendTextElement(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static String textOfChild(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }
}
