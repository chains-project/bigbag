package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String LOGBACK_CLASSIC_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String SLF4J_2_VERSION = "2.0.13";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .forEach(Main::updatePomIfNeeded);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree", e);
        }
    }

    private static void updatePomIfNeeded(Path pomPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(Files.newInputStream(pomPath));

            Element project = document.getDocumentElement();
            Element dependencies = firstDirectChild(project, "dependencies");
            if (dependencies == null) {
                return;
            }

            boolean changed = removeDirectProjectDependencies(project, dependencies);

            NodeList dependencyNodes = dependencies.getElementsByTagName("dependency");

            boolean hasCompatibleLogback = false;
            Element slf4jApiDependency = null;
            Element slf4jApiVersion = null;
            Element logbackCoreDependency = null;
            Element logbackCoreVersion = null;

            for (int i = 0; i < dependencyNodes.getLength(); i++) {
                Node node = dependencyNodes.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }

                Element dependency = (Element) node;
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                String version = childText(dependency, "version");

                if (LOGBACK_CLASSIC_GROUP_ID.equals(groupId)
                        && LOGBACK_CLASSIC_ARTIFACT_ID.equals(artifactId)
                        && version != null
                        && version.startsWith("1.4.")) {
                    hasCompatibleLogback = true;
                }

                if (LOGBACK_CLASSIC_GROUP_ID.equals(groupId) && LOGBACK_CORE_ARTIFACT_ID.equals(artifactId)) {
                    logbackCoreDependency = dependency;
                    logbackCoreVersion = childElement(dependency, "version");
                }

                if (SLF4J_GROUP_ID.equals(groupId) && SLF4J_ARTIFACT_ID.equals(artifactId)) {
                    slf4jApiDependency = dependency;
                    slf4jApiVersion = childElement(dependency, "version");
                }
            }

            if (!hasCompatibleLogback || slf4jApiDependency == null) {
                return;
            }

            String currentVersion = slf4jApiVersion == null ? null : slf4jApiVersion.getTextContent().trim();
            if (currentVersion == null || currentVersion.isEmpty() || currentVersion.startsWith("1.7.")) {
                if (slf4jApiVersion == null) {
                    slf4jApiVersion = document.createElement("version");
                    slf4jApiDependency.appendChild(slf4jApiVersion);
                }
                slf4jApiVersion.setTextContent(SLF4J_2_VERSION);
                changed = true;
            }

            if (logbackCoreDependency == null) {
                logbackCoreDependency = document.createElement("dependency");
                Element groupId = document.createElement("groupId");
                groupId.setTextContent(LOGBACK_CLASSIC_GROUP_ID);
                Element artifactId = document.createElement("artifactId");
                artifactId.setTextContent(LOGBACK_CORE_ARTIFACT_ID);
                Element version = document.createElement("version");
                version.setTextContent("1.4.6");
                logbackCoreDependency.appendChild(groupId);
                logbackCoreDependency.appendChild(artifactId);
                logbackCoreDependency.appendChild(version);
                dependencies.appendChild(logbackCoreDependency);
                changed = true;
            } else {
                String coreVersion = logbackCoreVersion == null ? null : logbackCoreVersion.getTextContent().trim();
                if (coreVersion == null || coreVersion.isEmpty() || !coreVersion.startsWith("1.4.")) {
                    if (logbackCoreVersion == null) {
                        logbackCoreVersion = document.createElement("version");
                        logbackCoreDependency.appendChild(logbackCoreVersion);
                    }
                    logbackCoreVersion.setTextContent("1.4.6");
                    changed = true;
                }
            }

            if (changed) {
                writeDocument(document, pomPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update " + pomPath, e);
        }
    }

    private static String childText(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    private static Element firstDirectChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static boolean removeDirectProjectDependencies(Element project, Element dependencies) {
        boolean changed = false;
        NodeList children = project.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node node = children.item(i);
            if (node instanceof Element && "dependency".equals(node.getNodeName())) {
                project.removeChild(node);
                dependencies.appendChild(node);
                changed = true;
            }
        }
        return changed;
    }

    private static void writeDocument(Document document, Path path) throws Exception {
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        try (var out = Files.newOutputStream(path)) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }
}
