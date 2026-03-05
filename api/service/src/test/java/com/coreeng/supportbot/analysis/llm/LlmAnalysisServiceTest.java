package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import com.coreeng.supportbot.summarydata.ThreadService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmAnalysisServiceTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private ThreadService threadService;

    private LlmAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new LlmAnalysisService(chatLanguageModel, threadService);
    }

    @Test
    void analyzeThread_shouldReturnParsedAnalysisRecord() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 100L;
        String prompt = "Analyze this ticket and provide driver, category, feature, and reason.";
        String threadText = "User: My pods are not starting\nSupport: Check the logs";

        String llmResponse = """
                Ticket: 100
                Primary Driver: Knowledge Gap
                Category: Monitoring & Troubleshooting Tenant Applications
                Platform Feature: workload compute
                Reason: Tenant's pods were failing due to insufficient timeout waiting for startup.
                """;

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo(100);
        assertThat(result.driver()).isEqualTo("Knowledge Gap");
        assertThat(result.category()).isEqualTo("Monitoring & Troubleshooting Tenant Applications");
        assertThat(result.feature()).isEqualTo("workload compute");
        assertThat(result.summary())
                .isEqualTo("Tenant's pods were failing due to insufficient timeout waiting for startup.");
        assertThat(result.promptId()).isNull(); // promptId is set by caller

        verify(threadService).getThreadAsText(channelId, threadTs);
        verify(chatLanguageModel).generate(anyString());
    }

    @Test
    void analyzeThread_shouldHandleMinimalResponse() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 200L;
        String prompt = "Analyze this ticket.";
        String threadText = "Simple question";

        String llmResponse = """
                Primary Driver: Bug
                Category: Configuration
                Platform Feature: networking
                Reason: Network configuration issue
                """;

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo(200);
        assertThat(result.driver()).isEqualTo("Bug");
        assertThat(result.category()).isEqualTo("Configuration");
        assertThat(result.feature()).isEqualTo("networking");
        assertThat(result.summary()).isEqualTo("Network configuration issue");
    }

    @Test
    void analyzeThread_shouldReturnNullWhenThreadServiceFails() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 300L;
        String prompt = "Analyze this ticket.";

        when(threadService.getThreadAsText(channelId, threadTs)).thenThrow(new RuntimeException("Slack API error"));

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNull();
        verify(threadService).getThreadAsText(channelId, threadTs);
        verifyNoInteractions(chatLanguageModel);
    }

    @Test
    void analyzeThread_shouldReturnNullWhenLlmFails() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 400L;
        String prompt = "Analyze this ticket.";
        String threadText = "Some thread content";

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenThrow(new RuntimeException("LLM API error"));

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNull();
        verify(threadService).getThreadAsText(channelId, threadTs);
        verify(chatLanguageModel).generate(anyString());
    }

    @Test
    void analyzeThread_shouldHandleResponseWithExtraWhitespace() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 500L;
        String prompt = "Analyze this ticket.";
        String threadText = "Thread content";

        String llmResponse = """
                Primary Driver:   Knowledge Gap  \s
                Category:  Monitoring  \s
                Platform Feature:   compute  \s
                Reason:   Summary with spaces  \s
                """;

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.driver()).isEqualTo("Knowledge Gap");
        assertThat(result.category()).isEqualTo("Monitoring");
        assertThat(result.feature()).isEqualTo("compute");
        assertThat(result.summary()).isEqualTo("Summary with spaces");
    }

    @Test
    void analyzeThread_shouldIgnoreTicketLineFromLlm() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 600L;
        String prompt = "Analyze this ticket.";
        String threadText = "Thread content";

        String llmResponse = """
                Ticket: 999
                Primary Driver: Bug
                Category: Config
                Platform Feature: storage
                Reason: Storage issue
                """;

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.ticketId()).isEqualTo(600); // Uses parameter, not LLM response
    }

    @Test
    void analyzeThread_shouldHandleWindowsLineEndings() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 700L;
        String prompt = "Analyze this ticket.";
        String threadText = "Thread content";

        String llmResponse = "Primary Driver: Bug\r\nCategory: Config\r\nPlatform Feature: storage\r\nReason: Issue";

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.driver()).isEqualTo("Bug");
        assertThat(result.category()).isEqualTo("Config");
        assertThat(result.feature()).isEqualTo("storage");
        assertThat(result.summary()).isEqualTo("Issue");
    }

    @Test
    void analyzeThread_shouldHandlePartialResponse() {
        // given
        String channelId = "C123456";
        String threadTs = "1234.5678";
        Long ticketId = 800L;
        String prompt = "Analyze this ticket.";
        String threadText = "Thread content";

        String llmResponse = """
                Primary Driver: Knowledge Gap
                Category: Monitoring
                """;

        when(threadService.getThreadAsText(channelId, threadTs)).thenReturn(threadText);
        when(chatLanguageModel.generate(anyString())).thenReturn(llmResponse);

        // when
        AnalysisRecord result = service.analyzeThread(channelId, threadTs, ticketId, prompt);

        // then
        assertThat(result).isNotNull();
        assertThat(result.driver()).isEqualTo("Knowledge Gap");
        assertThat(result.category()).isEqualTo("Monitoring");
        assertThat(result.feature()).isNull();
        assertThat(result.summary()).isNull();
    }
}
