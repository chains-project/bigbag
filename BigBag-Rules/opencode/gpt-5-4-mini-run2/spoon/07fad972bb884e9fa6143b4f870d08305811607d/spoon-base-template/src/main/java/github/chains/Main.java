package github.chains;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String SLF4J_GROUP = "org.slf4j";
    private static final String SLF4J_ARTIFACT = "slf4j-api";
    private static final String TARGET_VERSION = "2.0.13";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]).toAbsolutePath().normalize() : Paths.get(".").toAbsolutePath().normalize();
        Path pom = root.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }

        var dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        var doc = dbf.newDocumentBuilder().parse(pom.toFile());
        var dependencies = doc.getElementsByTagName("dependency");
        boolean changed = false;

        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = dependencies.item(i);
            String groupId = null;
            String artifactId = null;
            var children = dependency.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                var child = children.item(j);
                if ("groupId".equals(child.getNodeName())) {
                    groupId = child.getTextContent().trim();
                } else if ("artifactId".equals(child.getNodeName())) {
                    artifactId = child.getTextContent().trim();
                } else if ("version".equals(child.getNodeName()) && SLF4J_GROUP.equals(groupId) && SLF4J_ARTIFACT.equals(artifactId)) {
                    String current = child.getTextContent().trim();
                    if (!current.startsWith("2.")) {
                        child.setTextContent(TARGET_VERSION);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(pom.toFile()));
        }
    }
}
