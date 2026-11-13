package github.chains;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
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
    private static final String FIXED_SLF4J_VERSION = "2.0.13";
    private static final String FIXED_LOGBACK_VERSION = "1.4.6";

    public static void main(String[] args) throws Exception {
        Path inputRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        List<Path> pomFiles = new ArrayList<>();
        collectPomFiles(inputRoot, pomFiles);

        for (Path pomFile : pomFiles) {
            if (updatePom(pomFile)) {
                System.out.println("Updated " + pomFile);
            }
        }
    }

    private static void collectPomFiles(Path root, List<Path> pomFiles) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        if (Files.isRegularFile(root) && root.getFileName().toString().equals("pom.xml")) {
            pomFiles.add(root);
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                    .forEach(pomFiles::add);
        }
    }

    private static boolean updatePom(Path pomPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(pomPath.toFile());
        boolean hasLogbackClassic = hasDependency(document, LOGBACK_GROUP, LOGBACK_ARTIFACT, "1.4.");
        if (!hasLogbackClassic) {
            return false;
        }

        boolean updated = false;
        updated |= ensureDependencyVersion(document, LOGBACK_GROUP, LOGBACK_CORE_ARTIFACT, FIXED_LOGBACK_VERSION);
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }
            if (!SLF4J_GROUP.equals(childText(dependency, "groupId"))) {
                continue;
            }
            if (!SLF4J_ARTIFACT.equals(childText(dependency, "artifactId"))) {
                continue;
            }
            String version = childText(dependency, "version");
            if (version == null || version.startsWith("1.7") || version.startsWith("1.")) {
                setChildText(document, dependency, "version", FIXED_SLF4J_VERSION);
                updated = true;
            }
        }

        if (hasDependency(document, LOGBACK_GROUP, LOGBACK_ARTIFACT, "1.4.")) {
            updated |= ensureDependencyVersion(document, LOGBACK_GROUP, LOGBACK_CORE_ARTIFACT, FIXED_LOGBACK_VERSION);
        }

        if (updated) {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
        }
        return updated;
    }

    private static boolean ensureDependencyVersion(Document document, String groupId, String artifactId, String version) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }
            if (groupId.equals(childText(dependency, "groupId"))
                    && artifactId.equals(childText(dependency, "artifactId"))) {
                setChildText(document, dependency, "version", version);
                return true;
            }
        }

        NodeList dependencyManagement = document.getElementsByTagName("dependencies");
        if (dependencyManagement.getLength() == 0) {
            return false;
        }
        Element dependenciesElement = (Element) dependencyManagement.item(0);
        Element dependency = document.createElement("dependency");
        appendChildText(document, dependency, "groupId", groupId);
        appendChildText(document, dependency, "artifactId", artifactId);
        appendChildText(document, dependency, "version", version);
        dependenciesElement.appendChild(dependency);
        return true;
    }

    private static boolean hasDependency(Document document, String groupId, String artifactId, String versionPrefix) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }
            if (groupId.equals(childText(dependency, "groupId"))
                    && artifactId.equals(childText(dependency, "artifactId"))) {
                String version = childText(dependency, "version");
                if (version != null && version.startsWith(versionPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        return children.item(0).getTextContent().trim();
    }

    private static void setChildText(Document document, Element parent, String tagName, String value) {
        NodeList children = parent.getElementsByTagName(tagName);
        Element child;
        if (children.getLength() == 0) {
            child = document.createElement(tagName);
            parent.appendChild(child);
        } else {
            child = (Element) children.item(0);
        }
        child.setTextContent(value);
    }

    private static void appendChildText(Document document, Element parent, String tagName, String value) {
        Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
