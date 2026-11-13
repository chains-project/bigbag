package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class Main {

    private static final String LOGBACK_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_ARTIFACT_ID = "logback-classic";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_VERSION = "2.0.9";

    public static void main(final String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-root> [output-root]");
        }

        final Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path outputRoot = args.length == 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputRoot;

        if (Files.notExists(inputRoot)) {
            throw new IllegalArgumentException("Input root does not exist: " + inputRoot);
        }

        if (outputRoot.equals(inputRoot)) {
            rewritePomFilesInPlace(inputRoot);
        } else {
            copyAndRewrite(inputRoot, outputRoot);
        }
    }

    private static void copyAndRewrite(final Path inputRoot, final Path outputRoot) throws Exception {
        Files.walkFileTree(inputRoot, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                final Path relative = inputRoot.relativize(dir);
                final Path targetDir = outputRoot.resolve(relative);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                final Path relative = inputRoot.relativize(file);
                final Path targetFile = outputRoot.resolve(relative);
                Files.createDirectories(targetFile.getParent());
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (file.getFileName().toString().equals("pom.xml")) {
                    try {
                        rewritePom(targetFile);
                    } catch (final Exception ex) {
                        throw new IOException("Failed to rewrite " + targetFile, ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewritePomFilesInPlace(final Path root) throws Exception {
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("pom.xml")) {
                    try {
                        rewritePom(file);
                    } catch (final Exception ex) {
                        throw new IOException("Failed to rewrite " + file, ex);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void rewritePom(final Path pomFile) throws Exception {
        final Document document = parseXml(pomFile);
        boolean changed = false;

        if (hasDependency(document, LOGBACK_GROUP_ID, LOGBACK_ARTIFACT_ID)) {
            changed |= updateSlf4jDependencies(document);
            changed |= updateSlf4jProperties(document);
        }

        if (changed) {
            writeXml(document, pomFile);
        }
    }

    private static boolean updateSlf4jDependencies(final Document document) {
        boolean changed = false;
        final NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            final Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element dependency = (Element) node;
            final String groupId = childText(dependency, "groupId");
            if (!SLF4J_GROUP_ID.equals(groupId)) {
                continue;
            }
            final String version = childText(dependency, "version");
            if (!SLF4J_VERSION.equals(version)) {
                setChildText(document, dependency, "version", SLF4J_VERSION);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean updateSlf4jProperties(final Document document) {
        boolean changed = false;
        final NodeList propertiesList = document.getElementsByTagName("properties");
        for (int i = 0; i < propertiesList.getLength(); i++) {
            final Node node = propertiesList.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element properties = (Element) node;
            final NodeList children = properties.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                final Node child = children.item(j);
                if (!(child instanceof Element)) {
                    continue;
                }
                final Element property = (Element) child;
                final String name = property.getTagName();
                if (name.toLowerCase().contains("slf4j") && !SLF4J_VERSION.equals(property.getTextContent().trim())) {
                    property.setTextContent(SLF4J_VERSION);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean hasDependency(final Document document, final String groupId, final String artifactId) {
        final NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            final Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            final Element dependency = (Element) node;
            if (groupId.equals(childText(dependency, "groupId")) && artifactId.equals(childText(dependency, "artifactId"))) {
                return true;
            }
        }
        return false;
    }

    private static Document parseXml(final Path pomFile) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setNamespaceAware(false);
        final Document document = factory.newDocumentBuilder().parse(pomFile.toFile());
        document.getDocumentElement().normalize();
        return document;
    }

    private static void writeXml(final Document document, final Path pomFile) throws Exception {
        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        final Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(pomFile.toFile()));
    }

    private static String childText(final Element parent, final String tagName) {
        final NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() == 0) {
            return "";
        }
        return children.item(0).getTextContent().trim();
    }

    private static void setChildText(final Document document, final Element parent, final String tagName, final String value) {
        final NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            children.item(0).setTextContent(value);
            return;
        }
        final Element child = document.createElement(tagName);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
