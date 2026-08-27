package com.coreeng.supportbot.summarydata.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisRecord;
import com.coreeng.supportbot.analysis.AnalysisResultsService;
import com.coreeng.supportbot.config.AnalysisProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SummaryDataControllerTest {

    @Mock
    private AnalysisResultsService analysisResultsService;

    private AnalysisProps analysisProps;
    private ObjectMapper objectMapper;
    private SummaryDataController controller;

    @BeforeEach
    void setUp() {
        AnalysisProps.Bundle bundle = new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip");
        AnalysisProps.Prompt prompt = new AnalysisProps.Prompt(true);
        analysisProps = new AnalysisProps(testLlm(), bundle, prompt);
        objectMapper = new ObjectMapper();
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);
    }

    // --- Import tests ---

    @Test
    void import_shouldParseJsonlAndReturnCount() {
        // given
        String jsonl = """
                {"ticketId":1,"driver":"Bug","category":"Auth","feature":"Login","summary":"Fix login"}
                {"ticketId":2,"driver":"Feature","category":"UI","feature":"Dashboard","summary":"Add chart"}""";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisResultsService.importAnalysisData(anyList())).thenReturn(2);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().recordsImported()).isEqualTo(2);
        assertThat(response.getBody().message()).isEqualTo("Import successful");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalysisRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analysisResultsService).importAnalysisData(captor.capture());

        List<AnalysisRecord> records = captor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).ticketId()).isEqualTo(1);
        assertThat(records.get(0).driver()).isEqualTo("Bug");
        assertThat(records.get(1).ticketId()).isEqualTo(2);
        assertThat(records.get(1).category()).isEqualTo("UI");
    }

    @Test
    void import_shouldSkipMalformedLines() {
        // given
        String jsonl = """
                {"ticketId":1,"driver":"Bug","category":"Auth","feature":"Login","summary":"Fix login"}
                this is not valid json
                {"ticketId":3,"driver":"Feature","category":"UI","feature":"Dashboard","summary":"Add chart"}""";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisResultsService.importAnalysisData(anyList())).thenReturn(2);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnalysisRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(analysisResultsService).importAnalysisData(captor.capture());

        List<AnalysisRecord> records = captor.getValue();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).ticketId()).isEqualTo(1);
        assertThat(records.get(1).ticketId()).isEqualTo(3);
    }

    @Test
    void import_shouldReturnBadRequest_whenFileIsEmpty() {
        // given
        MockMultipartFile file = new MockMultipartFile("file", "data.jsonl", "application/jsonl", new byte[0]);

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).containsIgnoringCase("empty");
        verifyNoInteractions(analysisResultsService);
    }

    @Test
    void import_shouldReturn500_whenDatabaseFails() {
        // given
        String jsonl =
                "{\"ticketId\":1,\"driver\":\"Bug\",\"category\":\"Auth\",\"feature\":\"Login\",\"summary\":\"Fix login\"}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "data.jsonl", "application/jsonl", jsonl.getBytes(StandardCharsets.UTF_8));

        when(analysisResultsService.importAnalysisData(anyList()))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        // when
        ResponseEntity<SummaryDataController.ImportResponse> response = controller.importAnalysisData(file);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Database error");
    }

    // --- Download tests ---

    @Test
    void download_shouldReturnClasspathResource() {
        // given - using default classpath resource from setUp

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"analysis.zip\"");
        assertThat(response.getBody()).isInstanceOf(Resource.class);
    }

    @Test
    void download_shouldReturnNotFound_whenClasspathResourceDoesNotExist() {
        // given
        analysisProps = new AnalysisProps(
                testLlm(), new AnalysisProps.Bundle("classpath:nonexistent.zip"), new AnalysisProps.Prompt(true));
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void download_shouldReturnZipFromDirectory(@TempDir Path tempDir) throws IOException {
        // given - create a directory with test files
        Files.writeString(tempDir.resolve("file1.txt"), "Content of file 1");
        Files.writeString(tempDir.resolve("file2.txt"), "Content of file 2");
        Files.writeString(tempDir.resolve("script.sh"), "#!/bin/bash\necho 'test'");

        analysisProps = new AnalysisProps(
                testLlm(), new AnalysisProps.Bundle(tempDir.toString()), new AnalysisProps.Prompt(true));
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"analysis.zip\"");
        assertThat(response.getBody()).isInstanceOf(byte[].class);

        // Verify zip contents
        byte[] zipBytes = (byte[]) response.getBody();
        List<String> fileNames = new ArrayList<>();
        List<String> fileContents = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileNames.add(entry.getName());
                fileContents.add(new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }

        assertThat(fileNames).containsExactlyInAnyOrder("file1.txt", "file2.txt", "script.sh");
        assertThat(fileContents)
                .containsExactlyInAnyOrder("Content of file 1", "Content of file 2", "#!/bin/bash\necho 'test'");
    }

    @Test
    void download_shouldReturnEmptyZip_whenDirectoryIsEmpty(@TempDir Path tempDir) throws IOException {
        // given - empty directory
        analysisProps = new AnalysisProps(
                testLlm(), new AnalysisProps.Bundle(tempDir.toString()), new AnalysisProps.Prompt(true));
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(byte[].class);

        // Verify zip is empty
        byte[] zipBytes = (byte[]) response.getBody();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            assertThat(zis.getNextEntry()).isNull();
        }
    }

    @Test
    void download_shouldSkipSubdirectories(@TempDir Path tempDir) throws IOException {
        // given - directory with files and subdirectories
        Files.writeString(tempDir.resolve("file1.txt"), "Content 1");
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("file2.txt"), "Content 2");

        analysisProps = new AnalysisProps(
                testLlm(), new AnalysisProps.Bundle(tempDir.toString()), new AnalysisProps.Prompt(true));
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify only top-level files are included
        byte[] zipBytes = (byte[]) response.getBody();
        List<String> fileNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                fileNames.add(entry.getName());
                zis.closeEntry();
            }
        }

        assertThat(fileNames).containsExactly("file1.txt");
    }

    @Test
    void download_shouldReturnNotFound_whenDirectoryDoesNotExist() {
        // given
        analysisProps = new AnalysisProps(
                testLlm(), new AnalysisProps.Bundle("/nonexistent/directory"), new AnalysisProps.Prompt(true));
        controller = new SummaryDataController(analysisProps, analysisResultsService, objectMapper);

        // when
        ResponseEntity<?> response = controller.download();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static AnalysisProps.Llm testLlm() {
        return new AnalysisProps.Llm(
                "gemini-2.5-flash",
                Duration.ofSeconds(1),
                new AnalysisProps.Vertex(true, "test-project", "europe-west2"),
                new AnalysisProps.Proxy(false, "", new AnalysisProps.Proxy.Auth(""), Duration.ofSeconds(30)));
    }
}
