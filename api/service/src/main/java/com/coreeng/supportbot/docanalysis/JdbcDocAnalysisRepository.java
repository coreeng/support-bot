package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.dbschema.Tables.DOC_ANALYSIS;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.dbschema.enums.TicketStatus;
import com.coreeng.supportbot.dbschema.tables.records.DocAnalysisRecord;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcDocAnalysisRepository implements DocAnalysisRepository {

    private final DSLContext dsl;

    @Override
    @Transactional
    public void saveResult(DocAnalysis.Result result) {
        DocAnalysisRecord row = new DocAnalysisRecord();
        row.setTicketId(result.ticketId().id());
        row.setPromptId(result.promptId());
        row.setClassification(result.classification().code());
        row.setSource(result.source());
        row.setDocsVersion(result.docsVersion());
        row.setQuestion(result.question());
        row.setAnswerSummary(result.answerSummary());
        row.setTags(result.tags());
        row.setProductAreas(result.productAreas());
        row.setDocPaths(result.docPaths());
        row.setEvidenceQuote(result.evidenceQuote());
        row.setRationale(result.rationale());
        row.setModel(result.model());
        row.setAnalysedAt(result.analysedAt());
        row.setAttempts(0);
        replace(row);
    }

    @Override
    @Transactional
    public void saveFailedAttempt(DocAnalysis.FailedAttempt attempt) {
        DocAnalysisRecord row = new DocAnalysisRecord();
        row.setTicketId(attempt.ticketId().id());
        row.setPromptId(attempt.promptId());
        row.setTags(List.of());
        row.setProductAreas(List.of());
        row.setDocPaths(List.of());
        row.setFailedAt(attempt.failedAt());
        row.setFailureReason(attempt.reasonCode());
        row.setAttempts(attempt.attempts());
        replace(row);
    }

    private void replace(DocAnalysisRecord row) {
        row.changed(true);
        dsl.insertInto(DOC_ANALYSIS)
                .set(row)
                .onConflict(DOC_ANALYSIS.TICKET_ID)
                .doUpdate()
                .set(row)
                .execute();
    }

    @Override
    public Optional<DocAnalysis> findByTicketId(TicketId ticketId) {
        return dsl.selectFrom(DOC_ANALYSIS)
                .where(DOC_ANALYSIS.TICKET_ID.eq(ticketId.id()))
                .fetchOptional()
                .map(JdbcDocAnalysisRepository::toDocAnalysis);
    }

    @Override
    public List<TicketToClassify> findDue(
            LocalDate from,
            LocalDate to,
            Collection<String> channelIds,
            String promptId,
            Instant failedBefore,
            int maxAttempts) {
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return dsl.select(TICKET.ID, QUERY.CHANNEL_ID, QUERY.TS)
                .from(TICKET)
                .join(QUERY)
                .on(QUERY.ID.eq(TICKET.QUERY_ID))
                .leftJoin(DOC_ANALYSIS)
                .on(DOC_ANALYSIS.TICKET_ID.eq(TICKET.ID))
                .where(TICKET.STATUS.eq(TicketStatus.closed))
                .and(QUERY.CHANNEL_ID.in(channelIds))
                .and(QUERY.DATE.ge(from.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .and(QUERY.DATE.lt(to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                .and(DOC_ANALYSIS
                        .TICKET_ID
                        .isNull()
                        .or(DOC_ANALYSIS.PROMPT_ID.ne(promptId))
                        .or(DOC_ANALYSIS.FAILED_AT.le(failedBefore).and(DOC_ANALYSIS.ATTEMPTS.lt(maxAttempts))))
                .orderBy(QUERY.DATE, TICKET.ID)
                .fetch(r ->
                        new TicketToClassify(new TicketId(r.get(TICKET.ID)), r.get(QUERY.CHANNEL_ID), r.get(QUERY.TS)));
    }

    @Override
    public int countAttemptedSince(Instant since) {
        return dsl.fetchCount(DOC_ANALYSIS, DOC_ANALYSIS.ANALYSED_AT.ge(since).or(DOC_ANALYSIS.FAILED_AT.ge(since)));
    }

    @Override
    @Transactional
    public void deleteFailedAttempts(Collection<TicketId> ticketIds, String reasonCode) {
        dsl.deleteFrom(DOC_ANALYSIS)
                .where(DOC_ANALYSIS.TICKET_ID.in(
                        ticketIds.stream().map(TicketId::id).toList()))
                .and(DOC_ANALYSIS.FAILURE_REASON.eq(reasonCode))
                .execute();
    }

    private static DocAnalysis toDocAnalysis(DocAnalysisRecord row) {
        TicketId ticketId = new TicketId(row.getTicketId());
        if (row.getFailedAt() != null) {
            return new DocAnalysis.FailedAttempt(
                    ticketId,
                    row.getPromptId(),
                    row.getFailedAt(),
                    requireNonNull(row.getFailureReason()),
                    row.getAttempts());
        }
        return new DocAnalysis.Result(
                ticketId,
                row.getPromptId(),
                DocClassification.fromCode(requireNonNull(row.getClassification())),
                requireNonNull(row.getSource()),
                row.getDocsVersion(),
                requireNonNull(row.getQuestion()),
                row.getAnswerSummary(),
                row.getTags(),
                row.getProductAreas(),
                row.getDocPaths(),
                row.getEvidenceQuote(),
                requireNonNull(row.getRationale()),
                requireNonNull(row.getModel()),
                requireNonNull(row.getAnalysedAt()));
    }
}
