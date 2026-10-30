package com.ceos24.autedi.aiexperiments;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** A 방식 연속 실험: 회전 보정 PNG로 전사 1회 후 이번 전사문을 첨삭 1회에 전달합니다. 수동 main 실행 전용이며 --check는 API를 호출하지 않습니다. */
public class TerraFlowExample {
    public static void main(String[] args) throws Exception {
        boolean checkOnly = args.length > 0 && args[0].equals("--check");
        int pathIndex = checkOnly ? 1 : 0;
        if (args.length - pathIndex > 1) {
            throw new IllegalArgumentException("사용법: [--check] [사진 경로]");
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path root = Files.isRegularFile(cwd.resolve("build.gradle")) ? cwd : cwd.resolve("autedi");
        if (!Files.isRegularFile(root.resolve("build.gradle"))) {
            throw new IllegalStateException("Working directory를 autedi 프로젝트 폴더로 지정하세요.");
        }
        Path source = (args.length > pathIndex ? Path.of(args[pathIndex]) : root.resolve("../test_data/test7.jpg"))
                .toAbsolutePath()
                .normalize();

        // 두 단계에 같은 회전 보정 PNG를 보내 좌표 기준을 일치시킵니다.
        var prepared = OcrTestImage.read(source);
        Path directory = Files.createTempDirectory(Files.createDirectories(root.resolve("build/ocr-results")), "flow-");
        Path image = directory.resolve("image.png");
        Files.write(image, prepared.png());
        System.out.println("Flow directory: " + directory);
        System.out.println("Source image: " + source);
        System.out.println("EXIF orientation: " + prepared.orientation());

        if (checkOnly) {
            TerraTextOcrExample.run(new String[] {"--check", image.toString()}, directory);
            // 실제 전사 결과가 없으므로 입력 검사 전용 텍스트로 첨삭 준비만 확인합니다.
            Path placeholder = directory.resolve("check-only.txt");
            Files.writeString(placeholder, "Local input check only.", StandardCharsets.UTF_8);
            TerraCorrectionExample.main(new String[] {"--check", image.toString(), placeholder.toString()});
            System.out.println("Flow local check passed. API requests: 0. No transcription or correction generated.");
            return;
        }

        long started = System.nanoTime();
        System.out.println("===== STEP 1/2: TRANSCRIPTION =====");
        Path transcript = TerraTextOcrExample.run(new String[] {image.toString()}, directory);
        if (Files.readString(transcript, StandardCharsets.UTF_8).isBlank()) {
            throw new IllegalStateException("전사문이 비어 있어 첨삭 요청을 보내지 않았습니다. 결과: " + directory);
        }
        System.out.println("===== STEP 2/2: CORRECTION (using the transcription just generated) =====");
        TerraCorrectionExample.main(new String[] {image.toString(), transcript.toString()});
        System.out.printf(
                "Flow completed. Total elapsed: %d ms (including local processing).%n",
                (System.nanoTime() - started) / 1_000_000);
        System.out.println("Flow inputs: " + directory);
        System.out.println("Correction outputs: see Result directory above. No automatic retries.");
    }
}
