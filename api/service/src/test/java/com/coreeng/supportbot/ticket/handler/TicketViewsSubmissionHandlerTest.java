package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.ticket.EscalateViewMapper;
import com.coreeng.supportbot.ticket.TicketConfirmSubmissionMapper;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.slack.api.app_backend.views.payload.ViewSubmissionPayload;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import com.slack.api.model.view.View;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketViewsSubmissionHandlerTest {
    @Mock
    private TicketProcessingService ticketProcessingService;
    @Mock
    private TicketSummaryViewMapper ticketSummaryViewMapper;
    @Mock
    private EscalateViewMapper escalateViewMapper;
    @Mock
    private TicketConfirmSubmissionMapper confirmSubmissionMapper;
    @Mock
    private ExecutorService executor;
    @Mock
    private RbacService rbacService;

    private TicketViewsSubmissionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TicketViewsSubmissionHandler(
            ticketProcessingService,
            ticketSummaryViewMapper,
            escalateViewMapper,
            confirmSubmissionMapper,
            executor,
            rbacService
        );
    }

    @Test
    void whenUserIsNotSupport_returnsEmptyResponse() {
        // given
        String userId = "U123456";
        ViewSubmissionContext context = mock(ViewSubmissionContext.class);
        ViewSubmissionRequest request = mock(ViewSubmissionRequest.class);

        when(context.getRequestUserId()).thenReturn(userId);
        when(rbacService.isSupportBySlackId(userId)).thenReturn(false);

        // when
        ViewSubmissionResponse response = handler.apply(request, context);

        // then
        assertEquals(new ViewSubmissionResponse(), response);
        verify(rbacService).isSupportBySlackId(userId);
        verifyNoInteractions(ticketProcessingService, ticketSummaryViewMapper, escalateViewMapper, confirmSubmissionMapper);
    }

    @Test
    void whenUserIsSupport_processesRequest() {
        // given
        String userId = "U123456";

        ViewSubmissionContext context = mock(ViewSubmissionContext.class);
        ViewSubmissionRequest request = mock(ViewSubmissionRequest.class);
        ViewSubmissionPayload payload = ViewSubmissionPayload
            .builder()
            .view(View.builder()
                .callbackId("something-random")
                .build())
            .build();

        when(context.getRequestUserId()).thenReturn(userId);
        when(rbacService.isSupportBySlackId(userId)).thenReturn(true);
        when(request.getPayload()).thenReturn(payload);

        // when
        ViewSubmissionResponse response = handler.apply(request, context);

        // then
        verify(rbacService).isSupportBySlackId(userId);
        assertFalse(response.getErrors().isEmpty());
        assertNull(response.getView());
    }
} 