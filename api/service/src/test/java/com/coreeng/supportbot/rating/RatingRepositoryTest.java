package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingInMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RatingRepositoryTest {

    private RatingInMemoryRepository ratingRepository;

    @BeforeEach
    void setUp() {
        ratingRepository = new RatingInMemoryRepository();
    }

    @Test
    void shouldInsertRatingAndReturnId() {
        // Given
        Rating rating = Rating.createNew(
                5,
                String.valueOf(Instant.now().getEpochSecond()),
                "closed",
                "anonymous123",
                "production blocking",
                new String[]{"ingress"},
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
        Rating rating = Rating.createNew(
                4,
                String.valueOf(Instant.now().getEpochSecond()),
                "opened",
                "anonymous456",
                "bau",
                new String[]{"ingress"},
                true
        );
        UUID savedId = ratingRepository.insertRating(rating);

        // When
        Rating found = ratingRepository.findById(savedId);

        // Then
        assertNotNull(found, "Should find the rating");
        assertEquals(savedId, found.id(), "Should have correct ID");
        assertEquals(4, found.rating(), "Should have correct rating value");
        assertEquals("opened", found.status(), "Should have correct status");
        assertEquals("bau", found.impact(), "Should have correct impact");
        assertEquals("ingress", found.tags()[0], "Should have correct tag");
        assertTrue(found.isEscalated(), "Should be escalated");
    }

    @Test
    void shouldReturnNullWhenRatingNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // When
        Rating result = ratingRepository.findById(nonExistentId);

        // Then
        assertNull(result, "Should return null for non-existent ID");
    }

    @Test
    void shouldHandleNullOptionalFields() {
        // Given
        Rating rating = Rating.createNew(
                3,
                String.valueOf(Instant.now().getEpochSecond()),
                "stale",
                "anonymous789",
                null, // null impact
                null, // null tags
                false
        );

        // When
        UUID savedId = ratingRepository.insertRating(rating);
        Rating found = ratingRepository.findById(savedId);

        // Then
        assertNotNull(found, "Should find the rating");
        assertNull(found.impact(), "Impact should be null");
        assertNull(found.tags(), "Tags should be null");
        assertEquals("stale", found.status(), "Status should be preserved");
    }

    @Test
    void shouldFindRatingsByStatus() {
        // Given
        Rating openedRating = Rating.createNew(5, "1000", "opened", "anon1", "production blocking", new String[]{"gatekeeper"}, false);
        Rating closedRating = Rating.createNew(3, "2000", "closed", "anon2", "low", new String[]{"jenkins"}, true);
        Rating anotherOpenedRating = Rating.createNew(4, "3000", "opened", "anon3", "bau", new String[]{"eks"}, false);
        
        ratingRepository.insertRating(openedRating);
        ratingRepository.insertRating(closedRating);
        ratingRepository.insertRating(anotherOpenedRating);

        // When
        var openedRatings = ratingRepository.findRatingsByStatus("opened");
        var closedRatings = ratingRepository.findRatingsByStatus("closed");

        // Then
        assertEquals(2, openedRatings.size(), "Should find 2 opened ratings");
        assertEquals(1, closedRatings.size(), "Should find 1 closed rating");
        
        assertTrue(openedRatings.stream().allMatch(r -> "opened".equals(r.status())), 
                "All found ratings should have opened status");
        assertTrue(closedRatings.stream().allMatch(r -> "closed".equals(r.status())), 
                "All found ratings should have closed status");
    }

    @Test
    void shouldFindRatingsByTag() {
        // Given
        Rating bugRating = Rating.createNew(2, "1000", "opened", "anon4", "production blocking", new String[]{"ingress"}, false);
        Rating featureRating = Rating.createNew(4, "2000", "closed", "anon5", "bau", new String[]{"new-feature"}, true);
        Rating anotherBugRating = Rating.createNew(1, "3000", "stale", "anon6", "production blocking", new String[]{"ingress"}, true);
        
        ratingRepository.insertRating(bugRating);
        ratingRepository.insertRating(featureRating);
        ratingRepository.insertRating(anotherBugRating);

        // When
        var bugRatings = ratingRepository.findRatingsByTag("ingress");
        var featureRatings = ratingRepository.findRatingsByTag("new-feature");

        // Then
        assertEquals(2, bugRatings.size(), "Should find 2 ingress ratings");
        assertEquals(1, featureRatings.size(), "Should find 1 new-feature rating");
        
        assertTrue(bugRatings.stream().allMatch(r -> r.tags() != null && r.tags().length > 0 && "ingress".equals(r.tags()[0])), 
                "All found ratings should have ingress tag");
        assertTrue(featureRatings.stream().allMatch(r -> r.tags() != null && r.tags().length > 0 && "new-feature".equals(r.tags()[0])), 
                "All found ratings should have new-feature tag");
    }

    @Test
    void shouldFindEscalatedRatings() {
        // Given
        Rating escalatedRating1 = Rating.createNew(1, "1000", "opened", "anon7", "production blocking", new String[]{"ingress"}, true);
        Rating normalRating = Rating.createNew(5, "2000", "closed", "anon8", "low", new String[]{"bau"}, false);
        Rating escalatedRating2 = Rating.createNew(2, "3000", "stale", "anon9", "production blocking", new String[]{"github"}, true);
        
        ratingRepository.insertRating(escalatedRating1);
        ratingRepository.insertRating(normalRating);
        ratingRepository.insertRating(escalatedRating2);

        // When
        var escalatedRatings = ratingRepository.findEscalatedRatings();

        // Then
        assertEquals(2, escalatedRatings.size(), "Should find 2 escalated ratings");
        assertTrue(escalatedRatings.stream().allMatch(Rating::isEscalated), 
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
        Rating rating1 = Rating.createNew(4, "1000", "opened", "anon10", "bau", new String[]{"github"}, false);
        Rating rating2 = Rating.createNew(3, "2000", "closed", "anon11", "low", new String[]{"argocd"}, true);
        
        // When
        UUID id1 = ratingRepository.insertRating(rating1);
        UUID id2 = ratingRepository.insertRating(rating2);

        // Then
        assertNotEquals(id1, id2, "Should generate unique IDs");
        assertNotNull(ratingRepository.findById(id1), "Should find first rating");
        assertNotNull(ratingRepository.findById(id2), "Should find second rating");
    }
}