package com.ceos24.autedi.global.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOException;
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

    static boolean isSupported(ImageReader reader, int imageCount, byte[] bytes) throws IOException {
        if (imageCount != 2) return false;
        IIOMetadata primary = reader.getImageMetadata(0);
        if (segments(primary, 0xe2).stream().noneMatch(data -> startsWith(data, MPF_HEADER))) return false;
        List<Document> auxiliary;
        try {
            auxiliary = xmp(segments(reader.getImageMetadata(1), 0xe1));
        } catch (IIOException e) {
            // 일부 기기(갤럭시 등)의 gain map 이미지는 APP1이 먼저 시작해 ImageIO가 메타데이터를 읽지 못한다.
            // MPF가 가리키는 위치의 보조 이미지 헤더를 직접 읽어 같은 기준으로 검증한다.
            auxiliary = xmp(auxiliaryApp1Segments(bytes));
        }
        for (Document metadata : auxiliary) {
            if (APPLE_GAIN_MAP.equals(property(metadata, APPLE_PIXELS, "AuxiliaryImageType"))) return true;
            if ("1.0".equals(property(metadata, ADOBE_GAIN_MAP, "Version")) && isSdrBase(metadata)) {
                for (Document base : xmp(segments(primary, 0xe1))) {
                    if ("1.0".equals(property(base, ADOBE_GAIN_MAP, "Version")) && isSdrBase(base)) return true;
                }
            }
        }
        return false;
    }

    /** MPF의 두 번째 이미지 위치를 찾아 그 이미지의 APP1 세그먼트(헤더만)를 바이트에서 직접 읽는다. */
    private static List<byte[]> auxiliaryApp1Segments(byte[] bytes) {
        int pos = 2;
        while (pos + 4 <= bytes.length && (bytes[pos] & 255) == 0xff) {
            int marker = bytes[pos + 1] & 255;
            if (marker == 0xda || marker == 0xd9) break;
            int length = ((bytes[pos + 2] & 255) << 8) | (bytes[pos + 3] & 255);
            if (length < 2 || pos + 2L + length > bytes.length) break;
            if (marker == 0xe2 && length >= 8 && startsWith(bytes, MPF_HEADER, pos + 4)) {
                return app1Segments(bytes, mpfSecondImageOffset(bytes, pos + 8, pos + 2 + length));
            }
            pos += 2 + length;
        }
        throw new IllegalArgumentException("HDR 보조 이미지의 위치를 찾을 수 없습니다.");
    }

    private static int mpfSecondImageOffset(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) throw new IllegalArgumentException("손상된 MPF 헤더입니다.");
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M'))
            throw new IllegalArgumentException("지원하지 않는 MPF 바이트 순서입니다.");
        long ifd = u32(b, tiff + 4, little);
        if (ifd < 8 || tiff + ifd + 2 > end) throw new IllegalArgumentException("손상된 MPF 위치입니다.");
        int count = u16(b, (int) (tiff + ifd), little);
        for (int i = 0; i < count; i++) {
            int entry = (int) (tiff + ifd + 2 + 12L * i);
            if (entry + 12 > end) break;
            if (u16(b, entry, little) != 0xb002) continue;
            long table = u32(b, entry + 8, little);
            long second = tiff + table + 16; // MP Entry 한 개가 16바이트이며 두 번째가 보조 이미지다.
            if (u32(b, entry + 4, little) < 32 || second + 12 > end)
                throw new IllegalArgumentException("HDR 보조 이미지 정보가 올바르지 않습니다.");
            long offset = u32(b, (int) (second + 8), little);
            long start = tiff + offset;
            if (offset == 0 || start + 4 > b.length) throw new IllegalArgumentException("HDR 보조 이미지 위치가 올바르지 않습니다.");
            return (int) start;
        }
        throw new IllegalArgumentException("HDR 보조 이미지 정보를 찾을 수 없습니다.");
    }

    private static List<byte[]> app1Segments(byte[] bytes, int start) {
        if ((bytes[start] & 255) != 0xff || (bytes[start + 1] & 255) != 0xd8)
            throw new IllegalArgumentException("HDR 보조 이미지가 JPEG가 아닙니다.");
        List<byte[]> result = new ArrayList<>();
        int pos = start + 2;
        while (pos + 4 <= bytes.length && (bytes[pos] & 255) == 0xff) {
            int marker = bytes[pos + 1] & 255;
            if (marker == 0xda || marker == 0xd9) break;
            int length = ((bytes[pos + 2] & 255) << 8) | (bytes[pos + 3] & 255);
            if (length < 2 || pos + 2L + length > bytes.length) break;
            if (marker == 0xe1) result.add(java.util.Arrays.copyOfRange(bytes, pos + 4, pos + 2 + length));
            pos += 2 + length;
        }
        return result;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int x = b[at] & 255, y = b[at + 1] & 255;
        return little ? (y << 8) | x : (x << 8) | y;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long r = 0;
        for (int i = 0; i < 4; i++) r |= (long) (b[at + (little ? i : 3 - i)] & 255) << (8 * i);
        return r;
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

    private static List<Document> xmp(List<byte[]> app1Segments) throws IOException {
        List<Document> result = new ArrayList<>();
        for (byte[] data : app1Segments) {
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
        return startsWith(value, prefix, 0);
    }

    private static boolean startsWith(byte[] value, byte[] prefix, int offset) {
        if (offset < 0 || value.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[offset + i] != prefix[i]) return false;
        return true;
    }
}
