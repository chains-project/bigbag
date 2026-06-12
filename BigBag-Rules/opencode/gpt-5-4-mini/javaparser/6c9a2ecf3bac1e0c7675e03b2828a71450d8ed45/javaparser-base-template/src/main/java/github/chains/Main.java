package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Main {

    private static final String MAPSTRUCT_GROUP_ID = "org.mapstruct";
    private static final String MAPSTRUCT_ARTIFACT_ID = "mapstruct";
    private static final String MAPSTRUCT_PROCESSOR_ARTIFACT_ID = "mapstruct-processor";

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        List<Path> modified = new ArrayList<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("pom.xml") && fixPom(file)) {
                    modified.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path file : modified) {
            System.out.println(file);
        }
    }

    private static boolean fixPom(Path pom) throws IOException {
        Document document = parseXml(pom);
        List<Element> dependencies = findElementsByName(document.getDocumentElement(), "dependency");

        String processorVersion = null;
        for (Element dependency : dependencies) {
            if (isDependency(dependency, MAPSTRUCT_GROUP_ID, MAPSTRUCT_PROCESSOR_ARTIFACT_ID)) {
                processorVersion = textOfChild(dependency, "version");
                if (processorVersion != null && !processorVersion.isBlank()) {
                    break;
                }
            }
        }

        if (processorVersion == null || processorVersion.isBlank()) {
            return false;
        }

        boolean changed = false;
        for (Element dependency : dependencies) {
            if (isDependency(dependency, MAPSTRUCT_GROUP_ID, MAPSTRUCT_ARTIFACT_ID)) {
                Element version = childElement(dependency, "version");
                if (version == null || !processorVersion.equals(version.getTextContent().trim())) {
                    if (version == null) {
                        version = document.createElement("version");
                        dependency.appendChild(version);
                    }
                    version.setTextContent(processorVersion);
                    changed = true;
                }
            }
        }

        if (changed) {
            writeXml(document, pom);
        }
        return changed;
    }

    private static Document parseXml(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(Files.newInputStream(file));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML: " + file, e);
        }
    }

    private static void writeXml(Document document, Path file) throws IOException {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.transform(new DOMSource(document), new StreamResult(Files.newOutputStream(file)));
        } catch (Exception e) {
            throw new IOException("Failed to write XML: " + file, e);
        }
    }

    private static List<Element> findElementsByName(Element root, String name) {
        List<Element> result = new ArrayList<>();
        collectElements(root, name, result);
        return result;
    }

    private static void collectElements(Element element, String name, List<Element> result) {
        if (name.equals(element.getTagName())) {
            result.add(element);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                collectElements((Element) child, name, result);
            }
        }
    }

    private static boolean isDependency(Element dependency, String groupId, String artifactId) {
        return groupId.equals(textOfChild(dependency, "groupId"))
                && artifactId.equals(textOfChild(dependency, "artifactId"));
    }

    private static String textOfChild(Element parent, String childName) {
        Element child = childElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && childName.equals(((Element) child).getTagName())) {
                return (Element) child;
            }
        }
        return null;
    }
}
