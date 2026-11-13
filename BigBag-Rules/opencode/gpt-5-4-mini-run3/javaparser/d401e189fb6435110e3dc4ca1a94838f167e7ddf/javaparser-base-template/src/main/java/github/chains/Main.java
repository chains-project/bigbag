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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_ARTIFACT_ID = "slf4j-api";
    private static final String COMPATIBLE_SLF4J_VERSION = "2.0.13";

    public static void main(String[] args) {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

        try {
            int changedFiles = 0;
            int changedDependencies = 0;

            List<Path> pomFiles = new ArrayList<>();
            Files.walk(root)
                    .filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(pomFiles::add);

            for (Path pom : pomFiles) {
                int changed = rewritePom(pom);
                if (changed > 0) {
                    changedFiles++;
                    changedDependencies += changed;
                }
            }

            System.out.println("Updated files: " + changedFiles);
            System.out.println("Updated dependencies: " + changedDependencies);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int rewritePom(Path pomPath) throws Exception {
        Document document;
        try (InputStream in = Files.newInputStream(pomPath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(false);
            document = factory.newDocumentBuilder().parse(in);
        }

        int changed = 0;
        changed += updateSlf4jDependencies(document, "dependencyManagement");
        changed += updateSlf4jDependencies(document, "dependencies");

        if (changed > 0) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            try (OutputStream out = Files.newOutputStream(pomPath)) {
                transformer.transform(new DOMSource(document), new StreamResult(out));
            }
        }

        return changed;
    }

    private static int updateSlf4jDependencies(Document document, String containerTag) {
        int changes = 0;
        NodeList containers = document.getElementsByTagName(containerTag);
        for (int i = 0; i < containers.getLength(); i++) {
            Node container = containers.item(i);
            if (!(container instanceof Element)) {
                continue;
            }

            NodeList children = container.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof Element) || !"dependency".equals(child.getNodeName())) {
                    continue;
                }

                if (updateDependency((Element) child)) {
                    changes++;
                }
            }
        }
        return changes;
    }

    private static boolean updateDependency(Element dependency) {
        String groupId = textOfFirstChild(dependency, "groupId");
        String artifactId = textOfFirstChild(dependency, "artifactId");
        if (!SLF4J_GROUP_ID.equals(groupId) || !SLF4J_ARTIFACT_ID.equals(artifactId)) {
            return false;
        }

        Element version = firstChildElement(dependency, "version");
        if (version == null) {
            version = dependency.getOwnerDocument().createElement("version");
            version.setTextContent(COMPATIBLE_SLF4J_VERSION);
            dependency.appendChild(version);
            return true;
        }

        String current = version.getTextContent() == null ? "" : version.getTextContent().trim();
        if (current.startsWith("2.0.")) {
            return false;
        }

        version.setTextContent(COMPATIBLE_SLF4J_VERSION);
        return true;
    }

    private static Element firstChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static String textOfFirstChild(Element parent, String tagName) {
        Element child = firstChildElement(parent, tagName);
        return child == null || child.getTextContent() == null ? "" : child.getTextContent().trim();
    }
}
