package com.coreeng.supportbot.rbac;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.teams.SupportTeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RbacServiceTest {
    @Mock
    private SupportTeamService supportTeamService;

    @Test
    void isSupportBySlackId_whenRbacDisabled_returnsTrue() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(false), supportTeamService);
        SlackId.User slackId = SlackId.user("U123456");

        // when
        boolean result = rbacService.isSupportBySlackId(slackId);

        // then
        assertTrue(result);
        verifyNoInteractions(supportTeamService);
    }

    @Test
    void isSupportBySlackId_whenRbacEnabledAndUserIsSupport_returnsTrue() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(true), supportTeamService);
        SlackId.User slackId = SlackId.user("U123456");
        when(supportTeamService.isMemberByUserId(slackId)).thenReturn(true);

        // whe
        boolean result = rbacService.isSupportBySlackId(slackId);

        // Assert
        assertTrue(result);
        verify(supportTeamService).isMemberByUserId(slackId);
    }

    @Test
    void isSupportBySlackId_whenRbacEnabledAndUserIsNotSupport_returnsFalse() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(true), supportTeamService);
        SlackId.User slackId = SlackId.user("U123456");
        when(supportTeamService.isMemberByUserId(slackId)).thenReturn(false);

        // whe
        boolean result = rbacService.isSupportBySlackId(slackId);

        // Assert
        assertFalse(result);
        verify(supportTeamService).isMemberByUserId(slackId);
    }

    @Test
    void isSupportByEmail_whenRbacDisabled_returnsTrue() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(false), supportTeamService);
        String email = "user@example.com";

        // whe
        boolean result = rbacService.isSupportByEmail(email);

        // Assert
        assertTrue(result);
        verifyNoInteractions(supportTeamService);
    }

    @Test
    void isSupportByEmail_whenRbacEnabledAndUserIsSupport_returnsTrue() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(true), supportTeamService);
        String email = "user@example.com";
        when(supportTeamService.isMemberByUserEmail(email)).thenReturn(true);

        // whe
        boolean result = rbacService.isSupportByEmail(email);

        // Assert
        assertTrue(result);
        verify(supportTeamService).isMemberByUserEmail(email);
    }

    @Test
    void isSupportByEmail_whenRbacEnabledAndUserIsNotSupport_returnsFalse() {
        // given
        RbacService rbacService = new RbacService(new RbacProps(true), supportTeamService);
        String email = "user@example.com";
        when(supportTeamService.isMemberByUserEmail(email)).thenReturn(false);

        // when
        boolean result = rbacService.isSupportByEmail(email);

        // Assert
        assertFalse(result);
        verify(supportTeamService).isMemberByUserEmail(email);
    }
}
