package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentControllerTest {

    @Mock
    private TicketAssignmentProps assignmentProps;

    @InjectMocks
    private AssignmentController controller;

    @BeforeEach
    void setUp() {
        controller = new AssignmentController(assignmentProps);
    }

    @Test
    void shouldReturnTrueWhenAssignmentIsEnabled() {
        // given
        when(assignmentProps.enabled()).thenReturn(true);

        // when
        ResponseEntity<AssignmentStatusUI> response = controller.isAssignmentEnabled();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();

        verify(assignmentProps).enabled();
        verifyNoMoreInteractions(assignmentProps);
    }

    @Test
    void shouldReturnFalseWhenAssignmentIsDisabled() {
        // given
        when(assignmentProps.enabled()).thenReturn(false);

        // when
        ResponseEntity<AssignmentStatusUI> response = controller.isAssignmentEnabled();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();

        verify(assignmentProps).enabled();
        verifyNoMoreInteractions(assignmentProps);
    }
}

