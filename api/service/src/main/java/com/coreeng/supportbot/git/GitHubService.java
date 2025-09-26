package com.coreeng.supportbot.git;

import com.coreeng.supportbot.ticket.Ticket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubService {
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String REPOSITORY = "coreeng/support-bot";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void createIssue(Ticket ticket, List<Message> threadedMessages) throws IOException, InterruptedException {
        GitHub gh = new GitHubBuilder().withOAuthToken(System.getenv("GITHUB_OAUTH_TOKEN")).build();

        String querySummary = timedOllamaSummarisation(threadedMessages);

        GHRepository repository = gh.getRepository(REPOSITORY);

        String issueBody = getIssueBodyTemplate(ticket, querySummary);

        repository.createIssue("Documentation updates")
                .label("Documentation")
                .body(issueBody)
                .create();
    }

    private String getIssueBodyTemplate(Ticket ticket, String querySummary) {
        return """
                ### Motivation
                This documentation update request comes from a resolved support ticket.
                
                ### Context
                %s
                
                ### Related Tags
                %s
                
                ### Impact
                *%s*
                """.formatted(querySummary, ticket.tags(), ticket.impact());
    }

    private String timedOllamaSummarisation(List<Message> threadedMessages) throws IOException, InterruptedException {
        log.info("About to create summary");
        long start = System.currentTimeMillis();
        String resolution = ollamaSummarise(
                threadedMessages.get(0).getText().trim(),
                threadedMessages.get(2).getText().trim()
        );
        long duration = System.currentTimeMillis() - start;
        log.info("Created summary with duration: {}", duration);
        return resolution;
    }

    private String ollamaSummarise(String query, String resolution) throws IOException, InterruptedException {
        String prompt = buildPrompt(query, resolution);

        // Correct JSON body
        String payload = objectMapper.writeValueAsString(Map.of(
                "model", "llama3.1",
                "prompt", prompt,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode node = objectMapper.readTree(response.body());
        return node.path("response").asText().trim();
    }

    private String buildPrompt(String query, String resolution) {
        return String.format(
                "A tenant of our Kubernetes platform reported a problem and we provided a resolution. " +
                        "Produce a single, clear, and concise paragraph summarizing both the problem and the solution. " +
                        "Do not include any headers, quotes, and do not address a person. Just provide a descriptive text suitable for posting in a GitHub issue.\n" +
                        "Problem: %s\nSolution: %s",
                query,
                resolution
        );
    }
}

