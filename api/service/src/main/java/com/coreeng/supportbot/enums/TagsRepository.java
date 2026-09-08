package com.coreeng.supportbot.enums;

import static com.coreeng.supportbot.dbschema.Tables.TAG;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.ResultQuery;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TagsRepository {
    private final DSLContext dsl;

    public ImmutableList<Tag> listAll() {
        return fetchTags(dsl.select(TAG.LABEL, TAG.CODE, TAG.PRODUCT).from(TAG));
    }

    public ImmutableList<Tag> listAllActive() {
        return fetchTags(dsl.select(TAG.LABEL, TAG.CODE, TAG.PRODUCT).from(TAG).where(TAG.DELETED_AT.isNull()));
    }

    public ImmutableList<Tag> listByCodes(ImmutableCollection<String> codes) {
        if (codes.isEmpty()) {
            return ImmutableList.of();
        }
        return fetchTags(dsl.select(TAG.LABEL, TAG.CODE, TAG.PRODUCT).from(TAG).where(TAG.CODE.in(codes)));
    }

    public int insertOrActivate(ImmutableList<Tag> tags) {
        if (tags.isEmpty()) {
            return 0;
        }
        return dsl.insertInto(TAG, TAG.LABEL, TAG.CODE, TAG.PRODUCT)
                .valuesOfRows(tags.stream()
                        .map(t -> row(t.label(), t.code(), t.product()))
                        .toList())
                .onConflict(TAG.CODE)
                .doUpdate()
                .set(TAG.LABEL, excluded(TAG.LABEL))
                .set(TAG.PRODUCT, excluded(TAG.PRODUCT))
                .setNull(TAG.DELETED_AT)
                .execute();
    }

    public int deleteAllExcept(ImmutableList<String> codes) {
        if (codes.isEmpty()) {
            return 0;
        }
        return dsl.update(TAG)
                .set(TAG.DELETED_AT, Instant.now())
                .where(TAG.CODE.notIn(codes))
                .execute();
    }

    private ImmutableList<Tag> fetchTags(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(r -> new Tag(r.get(TAG.LABEL), r.get(TAG.CODE), Boolean.TRUE.equals(r.get(TAG.PRODUCT))))
                    .collect(toImmutableList());
        }
    }
}
