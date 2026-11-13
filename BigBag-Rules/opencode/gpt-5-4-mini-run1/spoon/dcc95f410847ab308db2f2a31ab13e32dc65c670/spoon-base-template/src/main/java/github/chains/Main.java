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

    private static final String LOGBACK_GROUP = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT = "logback-core";
    private static final String LOGBACK_ACCESS_ARTIFACT = "logback-access";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.5";

    public static void main(String[] args) throws Exception {
        Path sourceDir = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();

        for (Path pom : findPomFiles(sourceDir)) {
            updatePomIfNeeded(pom);
        }
    }

    private static List<Path> findPomFiles(Path root) throws Exception {
        List<Path> poms = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().equals("pom.xml"))
                    .forEach(poms::add);
        }
        return poms;
    }

    private static void updatePomIfNeeded(Path pom) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
        document.getDocumentElement().normalize();

        boolean hasLogback145 = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matches(dependency, LOGBACK_GROUP, LOGBACK_ARTIFACT)) {
                String version = textOfChild(dependency, "version");
                if (version != null && version.startsWith("1.4.")) {
                    hasLogback145 = true;
                }
            }
        }

        if (!hasLogback145) {
            return;
        }

        String logbackVersion = null;
        boolean updated = false;
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matches(dependency, SLF4J_GROUP, SLF4J_ARTIFACT)) {
                Element version = childElement(dependency, "version");
                if (version == null) {
                    version = document.createElement("version");
                    dependency.appendChild(version);
                }
                if (!TARGET_SLF4J_VERSION.equals(version.getTextContent().trim())) {
                    version.setTextContent(TARGET_SLF4J_VERSION);
                    updated = true;
                }
            } else if (matches(dependency, LOGBACK_GROUP, LOGBACK_ARTIFACT)) {
                String version = textOfChild(dependency, "version");
                if (version != null && version.startsWith("1.4.")) {
                    logbackVersion = version;
                }
            }
        }

        if (logbackVersion != null) {
            updated |= alignDependencyVersion(document, dependencies, LOGBACK_GROUP, LOGBACK_CORE_ARTIFACT, logbackVersion);
            updated |= alignDependencyVersion(document, dependencies, LOGBACK_GROUP, LOGBACK_ACCESS_ARTIFACT, logbackVersion);
            updated |= addDependencyIfMissing(document, dependencies, LOGBACK_GROUP, LOGBACK_CORE_ARTIFACT, logbackVersion);
        }

        if (updated) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
        }
    }

    private static boolean alignDependencyVersion(Document document, NodeList dependencies, String groupId, String artifactId, String versionValue) {
        boolean updated = false;
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matches(dependency, groupId, artifactId)) {
                Element version = childElement(dependency, "version");
                if (version == null) {
                    version = document.createElement("version");
                    dependency.appendChild(version);
                }
                if (!versionValue.equals(version.getTextContent().trim())) {
                    version.setTextContent(versionValue);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean addDependencyIfMissing(Document document, NodeList dependencies, String groupId, String artifactId, String versionValue) {
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (matches(dependency, groupId, artifactId)) {
                return false;
            }
        }

        NodeList dependencyNodes = document.getElementsByTagName("dependencies");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node node = dependencyNodes.item(i);
            if (node.getParentNode() == document.getDocumentElement() && node instanceof Element) {
                Element dependenciesNode = (Element) node;
                Element dependency = document.createElement("dependency");
                Element group = document.createElement("groupId");
                group.setTextContent(groupId);
                Element artifact = document.createElement("artifactId");
                artifact.setTextContent(artifactId);
                Element version = document.createElement("version");
                version.setTextContent(versionValue);
                dependency.appendChild(group);
                dependency.appendChild(artifact);
                dependency.appendChild(version);
                dependenciesNode.appendChild(dependency);
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Element dependency, String groupId, String artifactId) {
        return groupId.equals(textOfChild(dependency, "groupId"))
                && artifactId.equals(textOfChild(dependency, "artifactId"));
    }

    private static String textOfChild(Element parent, String tag) {
        Element child = childElement(parent, tag);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tag) {
        NodeList children = parent.getElementsByTagName(tag);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }
}
