package com.coreeng.supportbot.analysis.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.AnalysisService.AnalysisStatus;
import com.coreeng.supportbot.config.AnalysisProps;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTest {

    @Mock
    private AnalysisService analysisService;

    private AnalysisController controller;

    @BeforeEach
    void setUp() {
        AnalysisProps.Vertex vertex =
                new AnalysisProps.Vertex("test-project", "europe-west2", "gemini-2.5-flash", Duration.ofMillis(100));
        AnalysisProps.Bundle bundle = new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip");
        AnalysisProps.Prompt prompt = new AnalysisProps.Prompt(true, "", "");
        AnalysisProps analysisProps = new AnalysisProps(vertex, bundle, prompt);
        controller = new AnalysisController(analysisService, analysisProps);
    }

    @Test
    void runAnalysis_returns202_whenStartSucceeds() {
        when(analysisService.start(7)).thenReturn(true);

        ResponseEntity<Void> response = controller.runAnalysis(7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(analysisService).start(7);
    }

    @Test
    void runAnalysis_returns409_whenAlreadyRunning() {
        when(analysisService.start(7)).thenReturn(false);

        ResponseEntity<Void> response = controller.runAnalysis(7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(analysisService).start(7);
    }

    @Test
    void runAnalysis_returns400_whenDaysInvalid() {
        ResponseEntity<Void> zeroResponse = controller.runAnalysis(0);
        assertThat(zeroResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> overMaxResponse = controller.runAnalysis(366);
        assertThat(overMaxResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> negativeResponse = controller.runAnalysis(-1);
        assertThat(negativeResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(analysisService);
    }

    @Test
    void getKnowledgeGapsStatus_returnsEnabled() {
        ResponseEntity<?> response = controller.getKnowledgeGapsStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getStatus_returnsCurrentStatus() {
        AnalysisStatus status = new AnalysisStatus("analysis", 10, 5, true, null);
        when(analysisService.getStatus()).thenReturn(status);

        ResponseEntity<AnalysisController.AnalysisStatusResponse> response = controller.getStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AnalysisController.AnalysisStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.jobId()).isEqualTo("analysis");
        assertThat(body.exportedCount()).isEqualTo(10);
        assertThat(body.analyzedCount()).isEqualTo(5);
        assertThat(body.running()).isTrue();
        assertThat(body.error()).isNull();
    }
}
