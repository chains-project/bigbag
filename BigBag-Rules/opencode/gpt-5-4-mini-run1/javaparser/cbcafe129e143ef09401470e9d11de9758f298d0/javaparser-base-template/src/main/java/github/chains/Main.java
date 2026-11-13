package github.chains;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

public class Main {
    private static final String LOGBACK_CLASSIC_GROUP = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT = "logback-core";
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_SLF4J_VERSION = "2.0.7";
    private static final String TARGET_LOGBACK_VERSION = "1.4.6";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Source root does not exist or is not a directory: " + root);
            System.exit(1);
        }

        List<Path> changedFiles = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        if (upgradePom(file)) {
                            changedFiles.add(file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan " + root, e);
        }

        for (Path file : changedFiles) {
            System.out.println("Updated " + file);
        }
    }

    private static boolean upgradePom(Path pomFile) throws IOException {
        Document document = parseXml(pomFile);
        Element project = document.getDocumentElement();
        Element properties = firstChild(project, "properties");

        boolean hasLogbackClassic14 = false;
        boolean changed = false;

        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;

            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String version = childText(dependency, "version");

            if (LOGBACK_CLASSIC_GROUP.equals(groupId)
                    && LOGBACK_CLASSIC_ARTIFACT.equals(artifactId)
                    && isAtLeast14(resolveVersion(version, properties))) {
                hasLogbackClassic14 = true;
            }
        }

        if (!hasLogbackClassic14) {
            return false;
        }

        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;

            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            String version = childText(dependency, "version");

            if (SLF4J_GROUP.equals(groupId) && SLF4J_ARTIFACT.equals(artifactId)) {
                String resolvedVersion = resolveVersion(version, properties);
                if (!TARGET_SLF4J_VERSION.equals(resolvedVersion)) {
                    setVersion(dependency, version, properties, TARGET_SLF4J_VERSION);
                    changed = true;
                }
            } else if (LOGBACK_CLASSIC_GROUP.equals(groupId) && LOGBACK_CORE_ARTIFACT.equals(artifactId)) {
                String resolvedVersion = resolveVersion(version, properties);
                if (!TARGET_LOGBACK_VERSION.equals(resolvedVersion)) {
                    setVersion(dependency, version, properties, TARGET_LOGBACK_VERSION);
                    changed = true;
                }
            }
        }

        if (hasLogbackClassic14 && !hasDependency(dependencies, LOGBACK_CLASSIC_GROUP, LOGBACK_CORE_ARTIFACT)) {
            Element dependency = document.createElement("dependency");
            appendChildWithText(document, dependency, "groupId", LOGBACK_CLASSIC_GROUP);
            appendChildWithText(document, dependency, "artifactId", LOGBACK_CORE_ARTIFACT);
            appendChildWithText(document, dependency, "version", TARGET_LOGBACK_VERSION);
            Element dependenciesSection = firstChild(project, "dependencies");
            if (dependenciesSection != null) {
                dependenciesSection.appendChild(document.createTextNode("\n        "));
                dependenciesSection.appendChild(dependency);
                dependenciesSection.appendChild(document.createTextNode("\n    "));
            }
            changed = true;
        }

        if (changed) {
            writeXml(document, pomFile);
        }
        return changed;
    }

    private static Document parseXml(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(false);
            factory.setCoalescing(true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(in);
        } catch (Exception e) {
            throw new IOException("Failed to parse XML: " + path, e);
        }
    }

    private static void writeXml(Document document, Path path) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (Exception e) {
            throw new IOException("Failed to write XML: " + path, e);
        }
    }

    private static void appendChildWithText(Document document, Element parent, String name, String value) {
        Element child = document.createElement(name);
        child.setTextContent(value);
        parent.appendChild(document.createTextNode("\n            "));
        parent.appendChild(child);
        parent.appendChild(document.createTextNode("\n        "));
    }

    private static boolean hasDependency(NodeList dependencies, String groupId, String artifactId) {
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;
            if (groupId.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static Element firstChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element element = (Element) child;
                if (name.equals(element.getTagName())) {
                    return element;
                }
            }
        }
        return null;
    }

    private static String childText(Element parent, String name) {
        Element child = firstChild(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void setVersion(Element dependency, String currentVersion, Element properties, String newVersion) {
        Element versionElement = firstChild(dependency, "version");
        if (versionElement == null) {
            versionElement = dependency.getOwnerDocument().createElement("version");
            dependency.appendChild(versionElement);
        }

        if (currentVersion != null && currentVersion.startsWith("${") && currentVersion.endsWith("}") && properties != null) {
            String propertyName = currentVersion.substring(2, currentVersion.length() - 1);
            Element property = firstChild(properties, propertyName);
            if (property != null) {
                property.setTextContent(newVersion);
                return;
            }
        }

        versionElement.setTextContent(newVersion);
    }

    private static String resolveVersion(String version, Element properties) {
        if (version == null || properties == null) {
            return version;
        }
        if (version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            Element property = firstChild(properties, propertyName);
            if (property != null) {
                return property.getTextContent().trim();
            }
        }
        return version;
    }

    private static boolean isAtLeast14(String version) {
        return version != null && version.startsWith("1.4");
    }
}
