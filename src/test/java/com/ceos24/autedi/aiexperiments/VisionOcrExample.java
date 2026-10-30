package com.ceos24.autedi.aiexperiments;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Block;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageContext;
import com.google.cloud.vision.v1.Page;
import com.google.cloud.vision.v1.Paragraph;
import com.google.cloud.vision.v1.Symbol;
import com.google.cloud.vision.v1.TextAnnotation;
import com.google.cloud.vision.v1.Vertex;
import com.google.cloud.vision.v1.Word;
import com.google.protobuf.ByteString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Google Vision DOCUMENT_TEXT_DETECTION 비교 실험: 전사·단어 confidence·좌표 출력. main 실행 시 외부 API를 호출합니다. */
public class VisionOcrExample {

    public static void main(String[] args) throws Exception {
        // 1. 실제 사진이 있는 경로로 변경하세요.
        Path imagePath = Path.of(args.length > 0 ? args[0] : "../test_data/testdata.jpg")
                .toAbsolutePath()
                .normalize();
        System.out.println("Input image: " + imagePath);

        if (!Files.isRegularFile(imagePath)) {
            throw new IllegalArgumentException("이미지 파일을 찾을 수 없습니다: " + imagePath);
        }

        // 2. 사진 파일을 바이트로 읽습니다.
        byte[] imageBytes = Files.readAllBytes(imagePath);

        // 3. Google에 보낼 이미지 객체를 만듭니다.
        Image image =
                Image.newBuilder().setContent(ByteString.copyFrom(imageBytes)).build();

        // 4. 사용할 기능을 문서·손글씨 인식으로 지정합니다.
        Feature feature = Feature.newBuilder()
                .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                .build();

        // 영어 인식을 위한 참고 힌트이며, 영어만 반환하도록 강제하지는 않습니다.
        ImageContext imageContext =
                ImageContext.newBuilder().addLanguageHints("en").build();

        // 5. 이미지와 기능, 언어 힌트를 하나의 요청으로 묶습니다.
        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .setImage(image)
                .addFeatures(feature)
                .setImageContext(imageContext)
                .build();

        System.out.println("Google Vision에 OCR 요청을 보냅니다.");

        // 6. 앞서 설정한 ADC 인증 정보를 자동으로 사용합니다.
        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {

            BatchAnnotateImagesResponse response = client.batchAnnotateImages(List.of(request));

            if (response.getResponsesCount() == 0) {
                throw new IllegalStateException("OCR 응답이 없습니다.");
            }

            AnnotateImageResponse result = response.getResponses(0);

            // 요청은 도착했지만 이미지 처리에 실패했는지 확인합니다.
            if (result.hasError()) {
                throw new IllegalStateException(
                        "OCR 처리 실패: " + result.getError().getMessage());
            }

            // 7. 인식한 전체 문장을 출력합니다.
            String text = result.getFullTextAnnotation().getText();

            if (text.isBlank()) {
                System.out.println("이미지에서 글자를 찾지 못했습니다.");
                return;
            }

            System.out.println("===== OCR 인식 결과 =====");
            System.out.println(text);
            printWordCoordinates(result.getFullTextAnnotation());
        }
    }

    // Document Text Detection의 페이지 → 블록 → 문단 → 단어 구조를 순회합니다.
    private static void printWordCoordinates(TextAnnotation annotation) {
        System.out.println("===== WORD COORDINATES =====");
        int pageNumber = 0;
        int wordNumber = 0;
        for (Page page : annotation.getPagesList()) {
            pageNumber++;
            int width = page.getWidth();
            int height = page.getHeight();
            System.out.printf("Page %d: %d x %d pixels%n", pageNumber, width, height);

            for (Block block : page.getBlocksList()) {
                for (Paragraph paragraph : block.getParagraphsList()) {
                    for (Word word : paragraph.getWordsList()) {
                        // Google은 단어의 글자들을 symbols에 담아 반환합니다.
                        String wordText = word.getSymbolsList().stream()
                                .map(Symbol::getText)
                                .collect(Collectors.joining());
                        // 이 ID는 이번 실행 내 식별자이며 DB에 저장된 ID는 아닙니다.
                        System.out.printf(
                                Locale.ROOT,
                                "word_%03d | page=%d | text=[%s] | confidence=%.4f%n",
                                ++wordNumber,
                                pageNumber,
                                wordText,
                                word.getConfidence());

                        List<Vertex> vertices = word.getBoundingBox().getVerticesList();
                        if (vertices.size() != 4 || width <= 0 || height <= 0) {
                            System.out.println("  Coordinates unavailable: invalid polygon or page dimensions.");
                            continue;
                        }
                        // 꼭짓점 순서를 유지해 기울어진 단어도 사각형으로 표현합니다.
                        String pixels = vertices.stream()
                                .map(v -> String.format(Locale.ROOT, "(%d, %d)", v.getX(), v.getY()))
                                .collect(Collectors.joining(", "));
                        String normalized = vertices.stream()
                                .map(v -> String.format(
                                        Locale.ROOT,
                                        "(%.6f, %.6f)",
                                        v.getX() / (double) width,
                                        v.getY() / (double) height))
                                .collect(Collectors.joining(", "));
                        System.out.println("  pixels:     [" + pixels + "]");
                        System.out.println("  normalized: [" + normalized + "]");
                    }
                }
            }
        }
        System.out.println("Total words: " + wordNumber);
    }
}
