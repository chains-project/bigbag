package github.chains;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String OLD_GROUP_ID = "org.slf4j";
    private static final String OLD_ARTIFACT_ID = "slf4j-api";
    private static final String NEW_VERSION = "2.0.13";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        Path root = Paths.get(args[0]);
        List<Path> poms = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName() != null && path.getFileName().toString().equals("pom.xml"))
                 .forEach(poms::add);
        }
        for (Path pom : poms) {
            updatePom(pom);
        }
    }

    private static void updatePom(Path pom) {
        try (InputStream in = Files.newInputStream(pom)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            doc.getDocumentElement().normalize();
            boolean changed = false;

            NodeList dependencies = doc.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Node node = dependencies.item(i);
                if (!(node instanceof Element dependency)) {
                    continue;
                }
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (OLD_GROUP_ID.equals(groupId) && OLD_ARTIFACT_ID.equals(artifactId)) {
                    Element version = childElement(dependency, "version");
                    if (version != null && shouldRetarget(version.getTextContent())) {
                        version.setTextContent(NEW_VERSION);
                        changed = true;
                    }
                }
            }

            if (changed) {
                var transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                try (OutputStream out = Files.newOutputStream(pom)) {
                    transformer.transform(new DOMSource(doc), new StreamResult(out));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update " + pom, e);
        }
    }

    private static boolean shouldRetarget(String versionText) {
        if (versionText == null) {
            return true;
        }
        String version = versionText.trim();
        return version.isEmpty() || version.startsWith("1.7") || version.startsWith("${");
    }

    private static String childText(Element parent, String tagName) {
        Element child = childElement(parent, tagName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element childElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }
}
