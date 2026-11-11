package com.ceos24.autedi.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    @DisplayName("data URI로 반환하며 디코딩하면 원본 문자열과 같다")
    void dataUriRoundTrip() throws Exception {
        String content = "http://localhost:3000/qr-login?token=abc-DEF_123";

        String dataUri = generator.toDataUri(content, 300);

        assertThat(dataUri).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new MultiFormatReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))))
                .getText();
        assertThat(decoded).isEqualTo(content);
    }
}
