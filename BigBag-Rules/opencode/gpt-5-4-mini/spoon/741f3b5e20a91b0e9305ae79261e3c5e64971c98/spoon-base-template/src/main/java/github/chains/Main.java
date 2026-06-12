package github.chains;

import spoon.Launcher;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main {

    private static final String JACKSON_PREFIX = "com.fasterxml.jackson.";
    private static final String JACKSON_DATABIND = "jackson-databind";

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(projectRoot.toString());
        launcher.buildModel();

        List<Path> pomFiles = new ArrayList<>();
        try (var stream = Files.walk(projectRoot)) {
            stream.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .forEach(pomFiles::add);
        }

        for (Path pomFile : pomFiles) {
            updateJacksonVersions(pomFile);
        }
    }

    private static void updateJacksonVersions(Path pomFile) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile.toFile());
        document.getDocumentElement().normalize();

        String databindVersion = findJacksonVersion(document, JACKSON_DATABIND);
        if (databindVersion == null || databindVersion.isBlank()) {
            return;
        }

        boolean changed = false;
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);
            if (!(dependencyNode instanceof Element)) {
                continue;
            }
            Element dependency = (Element) dependencyNode;
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (groupId.startsWith(JACKSON_PREFIX) && !JACKSON_DATABIND.equals(artifactId)) {
                changed |= setChildText(dependency, "version", databindVersion);
            }
        }

        if (changed) {
            writeDocument(document, pomFile);
        }
    }

    private static String findJacksonVersion(Document document, String artifactId) {
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Node dependencyNode = dependencyNodes.item(i);
            if (!(dependencyNode instanceof Element)) {
                continue;
            }
            Element dependency = (Element) dependencyNode;
            String groupId = childText(dependency, "groupId");
            String currentArtifactId = childText(dependency, "artifactId");
            if ("com.fasterxml.jackson.core".equals(groupId) && artifactId.equals(currentArtifactId)) {
                return childText(dependency, "version");
            }
        }
        return null;
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        Node node = children.item(0);
        return node == null ? null : node.getTextContent().trim();
    }

    private static boolean setChildText(Element parent, String tagName, String value) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            Element child = parent.getOwnerDocument().createElement(tagName);
            child.setTextContent(value);
            parent.appendChild(child);
            return true;
        }
        Node node = children.item(0);
        if (node != null && !Objects.equals(node.getTextContent().trim(), value)) {
            node.setTextContent(value);
            return true;
        }
        return false;
    }

    private static void writeDocument(Document document, Path pomFile) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomFile.toFile()));
    }
}
