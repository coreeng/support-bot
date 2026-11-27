package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.dbschema.Tables.DOC_ANALYSIS;
import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.dbschema.tables.records.DocAnalysisRecord;
import com.coreeng.supportbot.ticket.TicketId;
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
