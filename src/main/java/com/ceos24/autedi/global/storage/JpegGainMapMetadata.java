package com.ceos24.autedi.global.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** 기본 JPEG + 명시된 HDR gain map 한 개만 허용한다. 보조 이미지의 픽셀은 디코딩하지 않는다. */
final class JpegGainMapMetadata {
    private static final String JPEG_METADATA = "javax_imageio_jpeg_image_1.0";
    private static final byte[] XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MPF_HEADER = {'M', 'P', 'F', 0};
    private static final String APPLE_PIXELS = "http://ns.apple.com/pixeldatainfo/1.0/";
    private static final String APPLE_GAIN_MAP = "urn:com:apple:photo:2020:aux:hdrgainmap";
    private static final String ADOBE_GAIN_MAP = "http://ns.adobe.com/hdr-gain-map/1.0/";

    private JpegGainMapMetadata() {}

    static boolean isSupported(ImageReader reader, int imageCount) throws IOException {
        if (imageCount != 2) return false;
        IIOMetadata primary = reader.getImageMetadata(0);
        if (segments(primary, 0xe2).stream().noneMatch(data -> startsWith(data, MPF_HEADER))) return false;
        List<Document> auxiliary = xmp(reader.getImageMetadata(1));
        for (Document metadata : auxiliary) {
            if (APPLE_GAIN_MAP.equals(property(metadata, APPLE_PIXELS, "AuxiliaryImageType"))) return true;
            if ("1.0".equals(property(metadata, ADOBE_GAIN_MAP, "Version")) && isSdrBase(metadata)) {
                for (Document base : xmp(primary)) {
                    if ("1.0".equals(property(base, ADOBE_GAIN_MAP, "Version")) && isSdrBase(base)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isSdrBase(Document metadata) {
        String value = property(metadata, ADOBE_GAIN_MAP, "BaseRenditionIsHDR");
        return value == null || "False".equalsIgnoreCase(value);
    }

    private static List<byte[]> segments(IIOMetadata metadata, int marker) {
        var root = (IIOMetadataNode) metadata.getAsTree(JPEG_METADATA);
        var nodes = root.getElementsByTagName("unknown");
        List<byte[]> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = (IIOMetadataNode) nodes.item(i);
            if (Integer.toString(marker).equals(node.getAttribute("MarkerTag"))
                    && node.getUserObject() instanceof byte[] bytes) result.add(bytes);
        }
        return result;
    }

    private static List<Document> xmp(IIOMetadata metadata) throws IOException {
        List<Document> result = new ArrayList<>();
        for (byte[] data : segments(metadata, 0xe1)) {
            if (!startsWith(data, XMP_HEADER)) continue;
            int end = data.length;
            while (end > XMP_HEADER.length && data[end - 1] == 0) end--;
            try {
                var factory = DocumentBuilderFactory.newDefaultInstance();
                factory.setNamespaceAware(true);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
                var builder = factory.newDocumentBuilder();
                builder.setErrorHandler(new DefaultHandler());
                result.add(builder.parse(new ByteArrayInputStream(data, XMP_HEADER.length, end - XMP_HEADER.length)));
            } catch (SAXException e) {
                throw new IllegalArgumentException("HDR 보조 이미지의 XMP 정보를 읽을 수 없습니다.");
            } catch (ParserConfigurationException e) {
                throw new IOException("HDR 메타데이터 파서를 준비할 수 없습니다.", e);
            }
        }
        return result;
    }

    /** XMP 속성은 접두사에 관계없이 namespace URI로 확인하며 attribute·element 표기를 모두 지원한다. */
    private static String property(Document metadata, String namespace, String name) {
        String value = null;
        var elements = metadata.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String candidate = null;
            if (namespace.equals(element.getNamespaceURI()) && name.equals(element.getLocalName()))
                candidate = element.getTextContent().trim();
            if (element.hasAttributeNS(namespace, name))
                candidate = element.getAttributeNS(namespace, name).trim();
            if (candidate == null) continue;
            if (value != null && !value.equals(candidate))
                throw new IllegalArgumentException("HDR 보조 이미지의 XMP 정보가 서로 다릅니다.");
            value = candidate;
        }
        return value;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }
}
