package github.chains;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            File pomFile = new File("/workspace/simplelocalize-cli/pom.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList dependencies = doc.getElementsByTagName("dependency");

            for (int i = 0; i < dependencies.getLength(); i++) {
                Node node = dependencies.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String groupId = getTagValue("groupId", element);
                    String artifactId = getTagValue("artifactId", element);

                    if ("com.fasterxml.jackson.core".equals(groupId) && "jackson-core".equals(artifactId)) {
                        setTagValue("version", element, "2.13.4");
                        System.out.println("Updated jackson-core version to 2.13.4");
                    }
                    if ("com.fasterxml.jackson.datatype".equals(groupId) && "jackson-datatype-jsr310".equals(artifactId)) {
                        setTagValue("version", element, "2.13.4");
                        System.out.println("Updated jackson-datatype-jsr310 version to 2.13.4");
                    }
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(pomFile);
            transformer.transform(source, result);
            System.out.println("POM file updated successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = (Node) nodeList.item(0);
        return node.getNodeValue();
    }

    private static void setTagValue(String tag, Element element, String value) {
        NodeList nodeList = element.getElementsByTagName(tag).item(0).getChildNodes();
        Node node = (Node) nodeList.item(0);
        node.setNodeValue(value);
    }
}