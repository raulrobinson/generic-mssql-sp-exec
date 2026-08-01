package com.raulbolivar.proxy.helper;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class XmlOutputConverter {

    public Object toJsonObject(String xml) {
        if (xml == null || xml.isBlank()) return xml;

        try {
            DocumentBuilderFactory factory = secureFactory();
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            Element root = document.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(root.getTagName(), convertElement(root));
            return result;
        } catch (Exception exception) {
            return xml;
        }
    }

    private Object convertElement(Element element) {
        List<Element> children = childElements(element);
        if (children.isEmpty()) return element.getTextContent().trim();

        Map<String, Object> result = new LinkedHashMap<>();

        for (Element child : children) {
            String name = child.getTagName();
            Object value = convertElement(child);
            Object current = result.get(name);

            if (current == null) {
                result.put(name, value);
            } else if (current instanceof List<?> currentList) {
                @SuppressWarnings("unchecked")
                List<Object> values = (List<Object>) currentList;
                values.add(value);
            } else {
                List<Object> values = new ArrayList<>();
                values.add(current);
                values.add(value);
                result.put(name, values);
            }
        }

        return result;
    }

    private List<Element> childElements(Element parent) {
        NodeList nodes = parent.getChildNodes();
        List<Element> children = new ArrayList<>();

        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE) children.add((Element) node);
        }

        return children;
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory;
    }
}
