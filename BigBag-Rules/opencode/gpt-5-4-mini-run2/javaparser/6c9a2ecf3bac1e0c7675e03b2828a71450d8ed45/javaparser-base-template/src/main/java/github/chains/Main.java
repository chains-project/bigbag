package github.chains;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
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

    private static final String MAPSTRUCT_GROUP_ID = "org.mapstruct";
    private static final String MAPSTRUCT_CORE_ARTIFACT = "mapstruct";
    private static final String MAPSTRUCT_PROCESSOR_ARTIFACT = "mapstruct-processor";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        try {
            List<Path> poms = new ArrayList<>();
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                    .forEach(poms::add);

            for (Path pom : poms) {
                alignMapStructVersions(pom);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to traverse Maven project at " + root, ex);
        }
    }

    private static void alignMapStructVersions(Path pomPath) {
        try (InputStream in = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);

            Document document = factory.newDocumentBuilder().parse(in);
            document.getDocumentElement().normalize();

            String processorVersion = findDependencyVersion(document, MAPSTRUCT_GROUP_ID, MAPSTRUCT_PROCESSOR_ARTIFACT);
            if (processorVersion == null) {
                return;
            }

            boolean changed = false;
            changed |= updateDependencyVersion(document, MAPSTRUCT_GROUP_ID, MAPSTRUCT_CORE_ARTIFACT, processorVersion);
            changed |= updateDependencyVersion(document, MAPSTRUCT_GROUP_ID, MAPSTRUCT_PROCESSOR_ARTIFACT, processorVersion);

            if (changed) {
                writeDocument(document, pomPath);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to align MapStruct versions in " + pomPath, ex);
        }
    }

    private static String findDependencyVersion(Document document, String groupId, String artifactId) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;
            if (matchesDependency(dependency, groupId, artifactId)) {
                String version = textOfChild(dependency, "version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        }
        return null;
    }

    private static boolean updateDependencyVersion(Document document, String groupId, String artifactId, String version) {
        boolean changed = false;
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element dependency = (Element) node;
            if (matchesDependency(dependency, groupId, artifactId)) {
                Element versionElement = childElement(dependency, "version");
                if (versionElement == null) {
                    versionElement = document.createElement("version");
                    dependency.appendChild(versionElement);
                }
                if (!version.equals(versionElement.getTextContent().trim())) {
                    versionElement.setTextContent(version);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean matchesDependency(Element dependency, String groupId, String artifactId) {
        return groupId.equals(textOfChild(dependency, "groupId"))
                && artifactId.equals(textOfChild(dependency, "artifactId"));
    }

    private static String textOfChild(Element parent, String name) {
        Element child = childElement(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static void writeDocument(Document document, Path pomPath) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        try (OutputStream out = Files.newOutputStream(pomPath)) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }
}
