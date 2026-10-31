package com.ceos24.autedi.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ceos24.autedi.domain.academy.entity.Academy;
import com.ceos24.autedi.domain.academy.entity.SubscriptionStatus;
import com.ceos24.autedi.domain.academy.repository.AcademyRepository;
import com.ceos24.autedi.domain.generation.repository.WorksheetGenerationRepository;
import com.ceos24.autedi.domain.user.entity.Role;
import com.ceos24.autedi.domain.user.entity.User;
import com.ceos24.autedi.domain.user.entity.UserStatus;
import com.ceos24.autedi.domain.user.repository.UserRepository;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionArea;
import com.ceos24.autedi.domain.worksheet.entity.CorrectionLevel;
import com.ceos24.autedi.domain.worksheet.entity.Worksheet;
import com.ceos24.autedi.domain.worksheet.entity.WorksheetSetting;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetPageRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetRepository;
import com.ceos24.autedi.domain.worksheet.repository.WorksheetSettingRepository;
import com.ceos24.autedi.domain.worksheet.service.PreparedWorksheetImage;
import com.ceos24.autedi.domain.worksheet.service.WorksheetService;
import com.ceos24.autedi.global.security.JwtTokenProvider;
import com.ceos24.autedi.global.storage.FileStorageService;
import com.ceos24.autedi.global.storage.LocalStorageService;
import com.ceos24.autedi.global.storage.WorksheetImagePreparer;
import com.ceos24.autedi.support.TestWorksheetImages;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** H2·임시 로컬 저장소만 사용해 업로드→저장→메타데이터→바이너리 조회를 검증한다. AI를 호출하지 않는다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:worksheet_images;DB_CLOSE_DELAY=-1;MODE=MySQL")
@Transactional
class WorksheetImageFlowTest {
    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> uploadRoot.toString());
        registry.add("app.backend-url", () -> "http://backend.test");
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    WebApplicationContext context;

    @Autowired
    AcademyRepository academies;

    @Autowired
    UserRepository users;

    @Autowired
    WorksheetRepository worksheets;

    @Autowired
    WorksheetPageRepository pages;

    @Autowired
    WorksheetSettingRepository settings;

    @Autowired
    WorksheetGenerationRepository generations;

    @Autowired
    WorksheetService service;

    @Autowired
    FileStorageService storage;

    @Autowired
    JwtTokenProvider tokens;

    @Autowired
    PlatformTransactionManager transactionManager;

    private MockMvc mvc;
    private Academy academy;
    private User user;
    private String bearer;
    private String otherBearer;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        academy = academies.save(Academy.builder()
                .name("이미지 테스트 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        user = user(academy);
        Academy other = academies.save(Academy.builder()
                .name("다른 학원")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .planExpiresAt(LocalDateTime.now().plusMonths(1))
                .build());
        User otherUser = user(other);
        bearer = bearer(user);
        otherBearer = bearer(otherUser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "PNG"})
    void uploadPreservesOriginalAndQueriesSamePngUsedByProcessingWithCorrectDimensions(String extension)
            throws Exception {
        boolean jpeg = !extension.equals("PNG");
        byte[] original = TestWorksheetImages.withOrientation(jpeg, 6, ByteOrder.BIG_ENDIAN);
        JsonNode response = upload("사진." + extension, original);
        long id = response.get("id").longValue();
        assertEquals("DRAFT", response.get("status").asString());
        assertEquals(
                "NORMALIZED_0_1", response.at("/pages/0/regionCoordinateUnit").asString());
        assertEquals(3, response.at("/pages/0/width").intValue());
        assertEquals(2, response.at("/pages/0/height").intValue());
        assertEquals(1, response.get("pages").size());
        String originalUrl = response.at("/file/originalUrl").asString();
        String imageUrl = response.at("/pages/0/imageUrl").asString();
        assertNotEquals(originalUrl, imageUrl);
        mvc.perform(get(path(originalUrl)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(content().bytes(original))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + LocalStorageService.sha256(original) + "\""));
        PreparedWorksheetImage processing = service.getPreparedImage(academy.getId(), id, 1);
        byte[] png = mvc.perform(get(path(imageUrl)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + processing.sha256() + "\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertArrayEquals(processing.png(), png);
        assertEquals(imageUrl, processing.imageUrl());
        assertEquals(3, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
        assertEquals(2, ImageIO.read(new ByteArrayInputStream(png)).getHeight());
        assertEquals(1, WorksheetImagePreparer.orientation(png, false));
        assertEquals(LocalStorageService.sha256(png), processing.sha256());
        processing.png()[0] = 0;
        assertArrayEquals(png, service.getPreparedImage(academy.getId(), id, 1).png());
        mvc.perform(get("/api/v1/worksheets/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.pages[0].imageUrl").value(imageUrl))
                .andExpect(jsonPath("$.result.pages[0].width").value(3))
                .andExpect(jsonPath("$.result.pages[0].height").value(2));
        Worksheet stored = worksheets.findById(id).orElseThrow();
        assertEquals(LocalStorageService.sha256(original), stored.getFileHash());
        assertArrayEquals(original, storage.load(stored.getFileUrl()).getContentAsByteArray());
        assertTrue(pages.findByWorksheetIdAndPageNumber(id, 1)
                .orElseThrow()
                .getPreviewImageUrl()
                .endsWith("-prepared.png"));
        assertEquals(0, generations.count());
    }

    @Test
    void replaceUpdatesPngAndDimensionsInvalidatesOcrAndKeepsSettingsAndOldOriginal() throws Exception {
        byte[] original = TestWorksheetImages.withOrientation(true, 6, ByteOrder.LITTLE_ENDIAN);
        JsonNode uploaded = upload("old.jpg", original);
        long id = uploaded.get("id").longValue();
        Worksheet worksheet = worksheets.findById(id).orElseThrow();
        String oldOriginal = worksheet.getFileUrl();
        var page = pages.findByWorksheetIdAndPageNumber(id, 1).orElseThrow();
        String oldPrepared = page.getPreviewImageUrl();
        page.updateOcrResult(Map.of("text", "previous"), "old-ocr-hash");
        pages.flush();
        settings.save(WorksheetSetting.builder()
                .worksheet(worksheet)
                .area(CorrectionArea.GRAMMAR_SPELLING)
                .level(CorrectionLevel.ADVANCED)
                .build());
        byte[] replacement = TestWorksheetImages.png(4, 2);
        String body = mvc.perform(multipart("/api/v1/worksheets/" + id + "/file")
                        .file(new MockMultipartFile("file", "new.png", "image/png", replacement))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .header(HttpHeaders.IF_MATCH, worksheet.getEtag()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.pages[0].width").value(4))
                .andExpect(jsonPath("$.result.pages[0].height").value(2))
                .andExpect(jsonPath("$.result.settings.areas[0].level").value("ADVANCED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode response = JSON.readTree(body).get("result");
        mvc.perform(get(path(response.at("/file/originalUrl").asString())).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(content().bytes(replacement));
        assertNull(page.getOcrResult());
        assertNull(page.getOcrFileHash());
        assertNotEquals(oldPrepared, page.getPreviewImageUrl());
        assertArrayEquals(original, storage.load(oldOriginal).getContentAsByteArray());
        assertTrue(WorksheetImagePreparer.isPng(storage.load(oldPrepared).getContentAsByteArray()));
        assertArrayEquals(
                storage.load(page.getPreviewImageUrl()).getContentAsByteArray(),
                service.getPreparedImage(academy.getId(), id, 1).png());
        assertEquals(0, generations.count());
    }

    @Test
    void fileEndpointsEnforceAuthenticationTenantAndPageBoundaries() throws Exception {
        long id = upload("photo.png", TestWorksheetImages.png(2, 3)).get("id").longValue();
        for (String url :
                new String[] {"/api/v1/worksheets/" + id + "/file", "/api/v1/worksheets/" + id + "/pages/1/image"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, otherBearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("WORKSHEET_001"));
        }
        mvc.perform(get("/api/v1/worksheets/" + id + "/pages/2/image").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAGE_001"));
    }

    @Test
    void invalidImageAndPdfNeverCreateWorksheetsOrReplaceValidImage() throws Exception {
        long before = worksheets.count();
        mvc.perform(multipart("/api/v1/worksheets")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "invalid".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_004"));
        mvc.perform(multipart("/api/v1/worksheets")
                        .file(new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_002"));
        assertEquals(before, worksheets.count());
        JsonNode uploaded = upload("valid.png", TestWorksheetImages.png(2, 3));
        long id = uploaded.get("id").longValue();
        byte[] png = service.getPreparedImage(academy.getId(), id, 1).png();
        mvc.perform(multipart("/api/v1/worksheets/" + id + "/file")
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", "bad".getBytes()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());
        assertArrayEquals(png, service.getPreparedImage(academy.getId(), id, 1).png());
    }

    @Test
    void rejectsMultipleImagesInsteadOfSilentlyUsingOnlyTheFirstOne() throws Exception {
        byte[] png = TestWorksheetImages.png(2, 3);
        long before = worksheets.count();
        mvc.perform(multipart("/api/v1/worksheets")
                        .file(new MockMultipartFile("file", "one.png", "image/png", png))
                        .file(new MockMultipartFile("file", "two.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        assertEquals(before, worksheets.count());
    }

    @Test
    void refusesLegacyUnpreparedImageAndMetadataMismatchInsteadOfInventingDimensions() throws Exception {
        long id = upload("photo.png", TestWorksheetImages.png(2, 3)).get("id").longValue();
        var page = pages.findByWorksheetIdAndPageNumber(id, 1).orElseThrow();
        String preparedUrl = page.getPreviewImageUrl();
        page.updatePreview(preparedUrl, 10, 10);
        mvc.perform(get("/api/v1/worksheets/" + id + "/pages/1/image").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_004"));
        page.updatePreview(worksheets.findById(id).orElseThrow().getFileUrl(), 2, 3);
        mvc.perform(get("/api/v1/worksheets/" + id + "/pages/1/image").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FILE_007"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseRollbackRemovesOnlyNewUploadFiles() throws Exception {
        long before = fileCount();
        AtomicReference<Long> rolledBackId = new AtomicReference<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                rolledBackId.set(service.uploadWorksheet(
                                user.getId(),
                                academy.getId(),
                                new MockMultipartFile(
                                        "file", "rollback.png", "image/png", TestWorksheetImages.png(2, 3)))
                        .id());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            status.setRollbackOnly();
        });
        assertTrue(worksheets.findById(rolledBackId.get()).isEmpty());
        assertEquals(before, fileCount());
    }

    private JsonNode upload(String name, byte[] bytes) throws Exception {
        String json = mvc.perform(multipart("/api/v1/worksheets")
                        .file(new MockMultipartFile("file", name, null, bytes))
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(json).get("result");
    }

    private User user(Academy academy) {
        return users.save(User.builder()
                .academy(academy)
                .email(UUID.randomUUID() + "@image-test.invalid")
                .passwordHash("unused-test-value")
                .name("이미지 테스트 사용자")
                .role(Role.TEACHER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private String bearer(User value) {
        return "Bearer "
                + tokens.createAccessToken(
                        value.getId(),
                        value.getAcademy().getId(),
                        value.getEmail(),
                        value.getRole().name());
    }

    private static String path(String url) {
        return URI.create(url).getPath();
    }

    private static long fileCount() throws Exception {
        try (var paths = Files.walk(uploadRoot)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
