package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory argument");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        int changedFiles = 0;
        List<Path> poms = new ArrayList<>();
        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                .forEach(poms::add);

            for (Path pom : poms) {
                if (alignMapStructVersions(pom)) {
                    changedFiles++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan source tree", e);
        }

        System.out.println("Updated " + changedFiles + " pom file(s).");
    }

    private static boolean alignMapStructVersions(Path pomPath) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);

            Document document = factory.newDocumentBuilder().parse(pomPath.toFile());
            document.getDocumentElement().normalize();

            String processorVersion = null;
            List<Element> mapstructVersionNodes = new ArrayList<>();
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Node node = dependencies.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }

                Element dependency = (Element) node;
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (!"org.mapstruct".equals(groupId)) {
                    continue;
                }

                if ("mapstruct-processor".equals(artifactId)) {
                    processorVersion = childText(dependency, "version");
                } else if ("mapstruct".equals(artifactId)) {
                    Element version = childElement(dependency, "version");
                    if (version != null) {
                        mapstructVersionNodes.add(version);
                    }
                }
            }

            if (processorVersion == null || processorVersion.isBlank() || mapstructVersionNodes.isEmpty()) {
                return false;
            }

            boolean changed = false;
            for (Element versionNode : mapstructVersionNodes) {
                String current = versionNode.getTextContent().trim();
                if (!processorVersion.equals(current)) {
                    versionNode.setTextContent(processorVersion);
                    changed = true;
                }
            }

            if (changed) {
                writeDocument(document, pomPath);
            }
            return changed;
        } catch (Exception e) {
            throw new IOException("Failed to update " + pomPath, e);
        }
    }

    private static String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && tagName.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static void writeDocument(Document document, Path path) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(document), new StreamResult(path.toFile()));
    }
}
