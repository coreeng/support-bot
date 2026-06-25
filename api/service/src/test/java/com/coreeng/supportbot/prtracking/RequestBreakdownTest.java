package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RequestBreakdownTest {

    @Test
    void constructsWithNestedSubsetCounts() {
        RequestBreakdown breakdown = new RequestBreakdown(100, 20, 4);

        assertThat(breakdown.totalSupportTickets()).isEqualTo(100);
        assertThat(breakdown.totalPrTickets()).isEqualTo(20);
        assertThat(breakdown.interventionPrTickets()).isEqualTo(4);
    }

    @Test
    void allowsZeros() {
        RequestBreakdown breakdown = new RequestBreakdown(0, 0, 0);

        assertThat(breakdown.totalSupportTickets()).isZero();
        assertThat(breakdown.totalPrTickets()).isZero();
        assertThat(breakdown.interventionPrTickets()).isZero();
    }

    @Test
    void allowsBoundaryWhereEveryTicketIsAnInterventionPr() {
        // the subset relationship is "<=", so all-equal is valid (every request was a PR that
        // needed escalation)
        RequestBreakdown breakdown = new RequestBreakdown(5, 5, 5);

        assertThat(breakdown.totalPrTickets()).isEqualTo(breakdown.totalSupportTickets());
        assertThat(breakdown.interventionPrTickets()).isEqualTo(breakdown.totalPrTickets());
    }

    @Test
    void rejectsNegativeTotalSupportTickets() {
        assertThatThrownBy(() -> new RequestBreakdown(-1, 0, 0))
                .isInstanceOf(NegativeCountException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsNegativeTotalPrTickets() {
        assertThatThrownBy(() -> new RequestBreakdown(0, -1, 0))
                .isInstanceOf(NegativeCountException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsNegativeInterventionPrTickets() {
        assertThatThrownBy(() -> new RequestBreakdown(0, 0, -1))
                .isInstanceOf(NegativeCountException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void rejectsMorePrTicketsThanTotalSupportTickets() {
        assertThatThrownBy(() -> new RequestBreakdown(5, 6, 0))
                .isInstanceOf(PrTicketsExceedSupportTicketsException.class)
                .hasMessageContaining("totalPrTickets must not exceed totalSupportTickets");
    }

    @Test
    void rejectsMoreInterventionsThanPrTickets() {
        assertThatThrownBy(() -> new RequestBreakdown(10, 3, 4))
                .isInstanceOf(InterventionExceedsPrTicketsException.class)
                .hasMessageContaining("interventionPrTickets must not exceed totalPrTickets");
    }

    @Test
    void allInvariantViolationsShareTheSealedBaseType() {
        // The controller advice catches the base type, so every concrete violation must extend it.
        assertThatThrownBy(() -> new RequestBreakdown(-1, 0, 0)).isInstanceOf(RequestBreakdownInvariantException.class);
        assertThatThrownBy(() -> new RequestBreakdown(5, 6, 0)).isInstanceOf(RequestBreakdownInvariantException.class);
        assertThatThrownBy(() -> new RequestBreakdown(10, 3, 4)).isInstanceOf(RequestBreakdownInvariantException.class);
    }
}
