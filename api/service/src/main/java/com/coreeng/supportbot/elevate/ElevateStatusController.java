package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import com.coreeng.supportbot.util.Page;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LEADERSHIP', 'SUPPORT_ENGINEER')")
public class ElevateStatusController {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_QUERY_LENGTH = 200;

    private final ElevateProps props;
    private final ElevateRepository repository;

    @GetMapping("/elevate/status")
    public ElevateStatusResponse status() {
        ElevateStoredStatus storedStatus = repository.getStoredStatus();
        ElevateSyncState state = storedStatus.state();
        return new ElevateStatusResponse(
                props.configured(),
                props.configured() ? props.baseUrl() : null,
                props.statusInterval().toString(),
                props.syncInterval().toString(),
                state.lastPingAttemptAt(),
                state.lastPingSuccessAt(),
                state.lastPingSucceeded(),
                state.lastPingError(),
                state.lastSyncAttemptAt(),
                state.lastSyncSuccessAt(),
                state.lastSyncSucceeded(),
                state.lastSyncError(),
                storedStatus.snapshotVersion(),
                storedStatus.counts(),
                storedStatus.integrity());
    }

    @GetMapping("/elevate/products")
    public Page<ElevateProductSummary> products(
            @RequestParam UUID snapshotVersion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String relationship,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return repository.findProducts(
                snapshotVersion, readQuery(page, pageSize, query, relationship, sort, direction));
    }

    @GetMapping("/elevate/products/{productId}")
    public ElevateProductSummary product(@RequestParam UUID snapshotVersion, @PathVariable String productId) {
        return repository.findProduct(snapshotVersion, productId).orElseThrow(() -> notFound("product"));
    }

    @GetMapping("/elevate/products/{productId}/journeys")
    public Page<ElevateJourneySummary> productJourneys(
            @RequestParam UUID snapshotVersion,
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String relationship,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return repository.findProductJourneys(
                snapshotVersion, productId, readQuery(page, pageSize, query, relationship, sort, direction));
    }

    @GetMapping("/elevate/products/{productId}/users")
    public Page<ElevateUserSummary> productUsers(
            @RequestParam UUID snapshotVersion,
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String relationship,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return repository.findProductUsers(
                snapshotVersion, productId, readQuery(page, pageSize, query, relationship, sort, direction));
    }

    @GetMapping("/elevate/journeys/{journeyId}")
    public ElevateJourneySummary journey(@RequestParam UUID snapshotVersion, @PathVariable String journeyId) {
        return repository.findJourney(snapshotVersion, journeyId).orElseThrow(() -> notFound("journey"));
    }

    @GetMapping("/elevate/journeys/{journeyId}/users")
    public Page<ElevateUserSummary> journeyUsers(
            @RequestParam UUID snapshotVersion,
            @PathVariable String journeyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String relationship,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return repository.findJourneyUsers(
                snapshotVersion, journeyId, readQuery(page, pageSize, query, relationship, sort, direction));
    }

    @GetMapping("/elevate/users/{userId}")
    public ElevateUserSummary user(@RequestParam UUID snapshotVersion, @PathVariable UUID userId) {
        return repository.findUser(snapshotVersion, userId).orElseThrow(() -> notFound("user"));
    }

    @GetMapping("/elevate/users/{userId}/journeys")
    public Page<ElevateJourneySummary> userJourneys(
            @RequestParam UUID snapshotVersion,
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String relationship,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return repository.findUserJourneys(
                snapshotVersion, userId, readQuery(page, pageSize, query, relationship, sort, direction));
    }

    @GetMapping("/elevate/integrity")
    public Page<ElevateIntegrityItem> integrity(
            @RequestParam UUID snapshotVersion,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        ElevateReadQuery readQuery = readQuery(page, pageSize, query, "all", sort, direction);
        return repository.findIntegrity(snapshotVersion, parseIntegrityType(type), readQuery);
    }

    private static ElevateReadQuery readQuery(
            int page, int pageSize, String query, String relationship, String sort, String direction) {
        if (page < 0) {
            throw badRequest("page must be zero or greater");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        String normalizedQuery = query.trim();
        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw badRequest("query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return new ElevateReadQuery(
                page,
                pageSize,
                normalizedQuery,
                parseRelationship(relationship),
                parseSort(sort),
                parseDirection(direction));
    }

    private static ElevateRelationshipFilter parseRelationship(String value) {
        return switch (normalized(value)) {
            case "all" -> ElevateRelationshipFilter.ALL;
            case "linked" -> ElevateRelationshipFilter.LINKED;
            case "unassigned" -> ElevateRelationshipFilter.UNASSIGNED;
            default -> throw badRequest("relationship must be all, linked, or unassigned");
        };
    }

    private static ElevateSort parseSort(String value) {
        return switch (normalized(value)) {
            case "name" -> ElevateSort.NAME;
            case "relationships" -> ElevateSort.RELATIONSHIPS;
            default -> throw badRequest("sort must be name or relationships");
        };
    }

    private static ElevateDirection parseDirection(String value) {
        return switch (normalized(value)) {
            case "asc" -> ElevateDirection.ASC;
            case "desc" -> ElevateDirection.DESC;
            default -> throw badRequest("direction must be asc or desc");
        };
    }

    private static ElevateIntegrityType parseIntegrityType(String value) {
        return switch (normalized(value)) {
            case "all" -> ElevateIntegrityType.ALL;
            case "orphanjourney" -> ElevateIntegrityType.ORPHAN_JOURNEY;
            case "orphanuser" -> ElevateIntegrityType.ORPHAN_USER;
            case "missingassignment" -> ElevateIntegrityType.MISSING_ASSIGNMENT;
            case "crossproductassignment" -> ElevateIntegrityType.CROSS_PRODUCT_ASSIGNMENT;
            default ->
                throw badRequest(
                        "type must be all, orphanJourney, orphanUser, missingAssignment, or crossProductAssignment");
        };
    }

    private static String normalized(String value) {
        return value.trim().replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Elevate " + resource + " not found");
    }
}
