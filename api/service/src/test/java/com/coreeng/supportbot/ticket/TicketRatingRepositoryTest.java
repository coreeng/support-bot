package com.coreeng.supportbot.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketRatingRepositoryTest {

    private TicketRatingInMemoryRepository ratingRepository;

    @BeforeEach
    void setUp() {
        ratingRepository = new TicketRatingInMemoryRepository();
    }

    @Test
    void shouldInsertRatingAndReturnId() {
        // Given
        TicketRating rating = TicketRating.createNew(
                5,
                String.valueOf(Instant.now().getEpochSecond()),
                "closed",
                "production blocking",
                "ingress",
                false
        );

        // When
        UUID savedId = ratingRepository.insertRating(rating);

        // Then
        assertNotNull(savedId, "Should return generated ID");
        assertEquals(1, ratingRepository.size(), "Should store one rating");
    }

    @Test
    void shouldFindRatingById() {
        // Given
        TicketRating rating = TicketRating.createNew(
                4,
                String.valueOf(Instant.now().getEpochSecond()),
                "opened",
                "bau",
                "ingress",
                true
        );
        UUID savedId = ratingRepository.insertRating(rating);

        // When
        TicketRating found = ratingRepository.findById(savedId);

        // Then
        assertNotNull(found, "Should find the rating");
        assertEquals(savedId, found.ratingId(), "Should have correct ID");
        assertEquals(4, found.rating(), "Should have correct rating value");
        assertEquals("opened", found.ticketStatusSnapshot(), "Should have correct status");
        assertEquals("bau", found.ticketImpactSnapshot(), "Should have correct impact");
        assertEquals("ingress", found.primaryTagSnapshot(), "Should have correct tag");
        assertTrue(found.escalated(), "Should be escalated");
    }

    @Test
    void shouldReturnNullWhenRatingNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        TicketRating result = ratingRepository.findById(nonExistentId);

        // Then
        assertNull(result, "Should return null for non-existent ID");
    }

    @Test
    void shouldHandleNullOptionalFields() {
        // Given
        TicketRating rating = TicketRating.createNew(
                3,
                String.valueOf(Instant.now().getEpochSecond()),
                "stale",
                null, // null impact
                null, // null tag
                false
        );

        // When
        UUID savedId = ratingRepository.insertRating(rating);
        TicketRating found = ratingRepository.findById(savedId);

        // Then
        assertNotNull(found, "Should find the rating");
        assertNull(found.ticketImpactSnapshot(), "Impact should be null");
        assertNull(found.primaryTagSnapshot(), "Tag should be null");
        assertEquals("stale", found.ticketStatusSnapshot(), "Status should be preserved");
    }

    @Test
    void shouldFindRatingsByStatus() {
        // Given
        TicketRating openedRating = TicketRating.createNew(5, "1000", "opened", "production blocking", "gatekeeper", false);
        TicketRating closedRating = TicketRating.createNew(3, "2000", "closed", "low", "jenkins", true);
        TicketRating anotherOpenedRating = TicketRating.createNew(4, "3000", "opened", "bau", "eks", false);
        
        ratingRepository.insertRating(openedRating);
        ratingRepository.insertRating(closedRating);
        ratingRepository.insertRating(anotherOpenedRating);

        // When
        var openedRatings = ratingRepository.findRatingsByStatus("opened");
        var closedRatings = ratingRepository.findRatingsByStatus("closed");

        // Then
        assertEquals(2, openedRatings.size(), "Should find 2 opened ratings");
        assertEquals(1, closedRatings.size(), "Should find 1 closed rating");
        
        assertTrue(openedRatings.stream().allMatch(r -> "opened".equals(r.ticketStatusSnapshot())), 
                "All found ratings should have opened status");
        assertTrue(closedRatings.stream().allMatch(r -> "closed".equals(r.ticketStatusSnapshot())), 
                "All found ratings should have closed status");
    }

    @Test
    void shouldFindRatingsByTag() {
        // Given
        TicketRating bugRating = TicketRating.createNew(2, "1000", "opened", "production blocking", "ingress", false);
        TicketRating featureRating = TicketRating.createNew(4, "2000", "closed", "bau", "new-feature", true);
        TicketRating anotherBugRating = TicketRating.createNew(1, "3000", "stale", "production blocking", "ingress", true);
        
        ratingRepository.insertRating(bugRating);
        ratingRepository.insertRating(featureRating);
        ratingRepository.insertRating(anotherBugRating);

        // When
        var bugRatings = ratingRepository.findRatingsByTag("ingress");
        var featureRatings = ratingRepository.findRatingsByTag("new-feature");

        // Then
        assertEquals(2, bugRatings.size(), "Should find 2 ingress ratings");
        assertEquals(1, featureRatings.size(), "Should find 1 new-feature rating");
        
        assertTrue(bugRatings.stream().allMatch(r -> "ingress".equals(r.primaryTagSnapshot())), 
                "All found ratings should have ingress tag");
        assertTrue(featureRatings.stream().allMatch(r -> "new-feature".equals(r.primaryTagSnapshot())), 
                "All found ratings should have new-feature tag");
    }

    @Test
    void shouldFindEscalatedRatings() {
        // Given
        TicketRating escalatedRating1 = TicketRating.createNew(1, "1000", "opened", "production blocking", "ingress", true);
        TicketRating normalRating = TicketRating.createNew(5, "2000", "closed", "low", "bau", false);
        TicketRating escalatedRating2 = TicketRating.createNew(2, "3000", "stale", "production blocking", "github", true);
        
        ratingRepository.insertRating(escalatedRating1);
        ratingRepository.insertRating(normalRating);
        ratingRepository.insertRating(escalatedRating2);

        // When
        var escalatedRatings = ratingRepository.findEscalatedRatings();

        // Then
        assertEquals(2, escalatedRatings.size(), "Should find 2 escalated ratings");
        assertTrue(escalatedRatings.stream().allMatch(TicketRating::escalated), 
                "All found ratings should be escalated");
    }

    @Test
    void shouldHandleEmptyResults() {
        // When
        var nonExistentStatus = ratingRepository.findRatingsByStatus("non-existent");
        var nonExistentTag = ratingRepository.findRatingsByTag("non-existent");
        var escalatedWhenNone = ratingRepository.findEscalatedRatings();

        // Then
        assertTrue(nonExistentStatus.isEmpty(), "Should return empty list for non-existent status");
        assertTrue(nonExistentTag.isEmpty(), "Should return empty list for non-existent tag");
        assertTrue(escalatedWhenNone.isEmpty(), "Should return empty list when no escalated ratings");
    }

    @Test
    void shouldGenerateUniqueIds() {
        // Given
        TicketRating rating1 = TicketRating.createNew(4, "1000", "opened", "bau", "github", false);
        TicketRating rating2 = TicketRating.createNew(3, "2000", "closed", "low", "argocd", true);
        
        // When
        UUID id1 = ratingRepository.insertRating(rating1);
        UUID id2 = ratingRepository.insertRating(rating2);

        // Then
        assertNotEquals(id1, id2, "Should generate unique IDs");
        assertNotNull(ratingRepository.findById(id1), "Should find first rating");
        assertNotNull(ratingRepository.findById(id2), "Should find second rating");
    }
}