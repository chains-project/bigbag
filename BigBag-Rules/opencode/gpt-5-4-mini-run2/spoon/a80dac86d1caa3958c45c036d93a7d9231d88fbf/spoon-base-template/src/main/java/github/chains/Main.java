package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String REQUIRED_SLF4J_VERSION = "2.0.13";
    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String LOGBACK_CORE_VERSION = "1.4.3";
    private static final String LOGBACK_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String LOGBACK_LEVEL = "ch.qos.logback.classic.Level";

    public static void main(String[] args) {
        Path inputPath = args.length > 0 ? Path.of(args[0]).toAbsolutePath().normalize() : Path.of(".").toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot(inputPath);

        if (!containsAffectedLogbackUsage(projectRoot)) {
            return;
        }

        Path pomPath = projectRoot.resolve("pom.xml");
        if (!Files.isRegularFile(pomPath)) {
            throw new IllegalStateException("pom.xml not found at project root: " + projectRoot);
        }

        try {
            if (upgradeSlf4jApiDependency(pomPath)) {
                System.out.println("Updated " + pomPath + " to use slf4j-api " + REQUIRED_SLF4J_VERSION);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update Maven dependencies", e);
        }
    }

    private static Path findProjectRoot(Path inputPath) {
        Path current = Files.isDirectory(inputPath) ? inputPath : inputPath.getParent();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return inputPath;
    }

    private static boolean containsAffectedLogbackUsage(Path projectRoot) {
        List<Path> sourceRoots = discoverSourceRoots(projectRoot);
        if (sourceRoots.isEmpty()) {
            return false;
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        for (Path sourceRoot : sourceRoots) {
            launcher.addInputResource(sourceRoot.toString());
        }

        launcher.buildModel();
        CtModel model = launcher.getModel();
        for (CtType<?> type : model.getAllTypes()) {
            List<CtTypeReference<?>> references = type.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> reference : references) {
                String qn = reference.getQualifiedName();
                if (LOGBACK_LOGGER.equals(qn) || LOGBACK_LEVEL.equals(qn)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<Path> discoverSourceRoots(Path projectRoot) {
        List<Path> roots = new ArrayList<>();
        Path mainJava = projectRoot.resolve("src/main/java");
        Path testJava = projectRoot.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            roots.add(mainJava);
        }
        if (Files.isDirectory(testJava)) {
            roots.add(testJava);
        }
        if (roots.isEmpty() && Files.isDirectory(projectRoot)) {
            roots.add(projectRoot);
        }
        return roots;
    }

    private static boolean upgradeSlf4jApiDependency(Path pomPath) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        document.getDocumentElement().normalize();

        boolean updated = false;
        Element project = document.getDocumentElement();
        Element dependenciesElement = firstDirectChildElement(project, "dependencies");
        if (dependenciesElement == null) {
            dependenciesElement = document.createElement("dependencies");
            project.appendChild(dependenciesElement);
        }

        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node dependency = dependencies.item(i);
            if (dependency.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element dependencyElement = (Element) dependency;
            String groupId = childText(dependencyElement, "groupId");
            String artifactId = childText(dependencyElement, "artifactId");
            if (!SLF4J_GROUP_ID.equals(groupId) || !SLF4J_ARTIFACT_ID.equals(artifactId)) {
                continue;
            }

            String version = childText(dependencyElement, "version");
            if (version == null || version.startsWith("1.7.")) {
                setChildText(document, dependencyElement, "version", REQUIRED_SLF4J_VERSION);
                updated = true;
            }
        }

        Element logbackCoreDependency = findDependencyElement(dependenciesElement, LOGBACK_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID);
        if (logbackCoreDependency == null) {
            logbackCoreDependency = document.createElement("dependency");
            appendChildElement(document, logbackCoreDependency, "groupId", LOGBACK_GROUP_ID);
            appendChildElement(document, logbackCoreDependency, "artifactId", LOGBACK_CORE_ARTIFACT_ID);
            appendChildElement(document, logbackCoreDependency, "version", LOGBACK_CORE_VERSION);
            dependenciesElement.appendChild(logbackCoreDependency);
            updated = true;
        } else {
            String version = childText(logbackCoreDependency, "version");
            if (version == null || !LOGBACK_CORE_VERSION.equals(version)) {
                setChildText(document, logbackCoreDependency, "version", LOGBACK_CORE_VERSION);
                updated = true;
            }
        }

        if (updated) {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
        }

        return updated;
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return null;
        }
        Node node = children.item(0);
        if (node == null) {
            return null;
        }
        String text = node.getTextContent();
        return text == null ? null : text.trim();
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

    private static Element firstDirectChildElement(Element parent, String tagName) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static Element findDependencyElement(Element dependenciesElement, String groupId, String artifactId) {
        Node child = dependenciesElement.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "dependency".equals(child.getNodeName())) {
                Element dependency = (Element) child;
                if (groupId.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                    return dependency;
                }
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static void appendChildElement(Document document, Element parent, String tagName, String value) {
        Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
