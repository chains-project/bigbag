package github.chains;

import spoon.Launcher;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final String LOGBACK_CLASSIC = "ch.qos.logback:logback-classic";
    private static final String LOGBACK_CORE = "ch.qos.logback:logback-core";
    private static final String SLF4J_API = "org.slf4j:slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.0";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <project-root>");
        }

        Path projectRoot = Path.of(args[0]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(projectRoot.resolve("src").toString());
        launcher.buildModel();

        Files.walk(projectRoot)
                .filter(path -> path.getFileName().toString().equals("pom.xml"))
                .forEach(pom -> {
                    try {
                        updatePom(pom);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    private static void updatePom(Path pomPath) throws Exception {
        if (!Files.isRegularFile(pomPath)) {
            return;
        }

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        document.getDocumentElement().normalize();

        String logbackVersion = null;
        Element slf4jDependency = null;
        Element logbackCoreDependency = null;

        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }

            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String coordinate = groupId + ":" + artifactId;

            if (LOGBACK_CLASSIC.equals(coordinate)) {
                String version = childText(dependency, "version");
                if (version != null && version.startsWith("1.4.")) {
                    logbackVersion = version;
                }
            } else if (SLF4J_API.equals(coordinate)) {
                slf4jDependency = dependency;
            } else if (LOGBACK_CORE.equals(coordinate)) {
                logbackCoreDependency = dependency;
            }
        }

        if (logbackVersion == null) {
            return;
        }

        ensureDependencyVersion(document, slf4jDependency, "org.slf4j", "slf4j-api", TARGET_SLF4J_VERSION);
        ensureDependencyVersion(document, logbackCoreDependency, "ch.qos.logback", "logback-core", logbackVersion);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
    }

    private static void ensureDependencyVersion(Document document, Element dependency, String groupId, String artifactId, String version) {
        if (dependency == null) {
            Element dependenciesElement = (Element) document.getElementsByTagName("dependencies").item(0);
            if (dependenciesElement == null) {
                return;
            }

            dependency = document.createElement("dependency");
            appendChildElement(document, dependency, "groupId", groupId);
            appendChildElement(document, dependency, "artifactId", artifactId);
            appendChildElement(document, dependency, "version", version);
            dependenciesElement.appendChild(dependency);
            return;
        }

        Node versionNode = childNode(dependency, "version");
        if (versionNode == null) {
            appendChildElement(document, dependency, "version", version);
        } else {
            versionNode.setTextContent(version);
        }
    }

    private static String childText(Element parent, String tagName) {
        Node node = childNode(parent, tagName);
        return node == null ? null : node.getTextContent();
    }

    private static Node childNode(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        return children.item(0);
    }

    private static void appendChildElement(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
