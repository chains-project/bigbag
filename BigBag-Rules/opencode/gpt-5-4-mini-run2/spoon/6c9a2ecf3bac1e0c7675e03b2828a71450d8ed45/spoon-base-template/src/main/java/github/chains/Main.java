package github.chains;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import spoon.Launcher;

public class Main {

    private static final String MAPSTRUCT_GROUP_ID = "org.mapstruct";
    private static final String MAPSTRUCT_ARTIFACT_ID = "mapstruct";
    private static final String MAPSTRUCT_PROCESSOR_ARTIFACT_ID = "mapstruct-processor";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        scanJavaSources(root);
        alignMapStructDependencies(root);
    }

    private static void scanJavaSources(Path root) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.endsWith(Paths.get("src", "main", "java"))) {
                        launcher.addInputResource(dir.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to discover source roots", e);
        }
        launcher.buildModel();
    }

    private static void alignMapStructDependencies(Path root) throws Exception {
        List<Path> poms = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("pom.xml")) {
                    poms.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path pom : poms) {
            updatePomIfNeeded(pom);
        }
    }

    private static void updatePomIfNeeded(Path pom) throws Exception {
        Document document = parseXml(pom);
        String processorVersion = findDependencyVersion(document, MAPSTRUCT_GROUP_ID, MAPSTRUCT_PROCESSOR_ARTIFACT_ID);
        if (processorVersion == null || processorVersion.isBlank()) {
            return;
        }

        boolean changed = false;
        changed |= setDependencyVersion(document, MAPSTRUCT_GROUP_ID, MAPSTRUCT_ARTIFACT_ID, processorVersion);

        if (changed) {
            writeXml(document, pom);
        }
    }

    private static Document parseXml(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(pom.toFile());
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            String currentGroupId = childText(dependency, "groupId");
            String currentArtifactId = childText(dependency, "artifactId");
            if (groupId.equals(currentGroupId) && artifactId.equals(currentArtifactId)) {
                return childText(dependency, "version");
            }
        }
        return null;
    }

    private static boolean setDependencyVersion(Document document, String groupId, String artifactId, String version) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            String currentGroupId = childText(dependency, "groupId");
            String currentArtifactId = childText(dependency, "artifactId");
            if (!groupId.equals(currentGroupId) || !artifactId.equals(currentArtifactId)) {
                continue;
            }

            Element versionElement = childElement(dependency, "version");
            if (versionElement == null) {
                versionElement = document.createElement("version");
                dependency.appendChild(versionElement);
            }
            if (!version.equals(versionElement.getTextContent())) {
                versionElement.setTextContent(version);
                changed = true;
            }
        }
        return changed;
    }

    private static Element childElement(Node node, String name) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String childText(Node node, String name) {
        Element child = childElement(node, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void writeXml(Document document, Path pom) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(pom)));
    }
}
