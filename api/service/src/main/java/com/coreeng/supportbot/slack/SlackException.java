package com.coreeng.supportbot.slack;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import com.google.common.collect.ImmutableList;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.SlackApiTextResponse;
import org.jspecify.annotations.Nullable;

public class SlackException extends RuntimeException {
    @Nullable private final SlackApiTextResponse response;

    private final ImmutableList<String> errorDetails;

    public SlackException(Throwable cause) {
        super(cause);
        if (cause instanceof SlackApiException exc) {
            response = exc.getError();
        } else {
            response = null;
        }
        errorDetails = ImmutableList.of();
    }

    public SlackException(SlackApiTextResponse response, ImmutableList<String> errorDetails) {
        super();
        this.response = response;
        this.errorDetails = checkNotNull(errorDetails);
    }

    @Override
    public String getMessage() {
        if (response != null) {
            return format(
                    """
                    Slack API returned an error:%n\
                      Error: %s%n\
                      Details: %s%n\
                      Warning: %s%n\
                      Needed: %s%n\
                      Provided: %s%n\
                    """,
                    response.getError(),
                    errorDetails,
                    response.getWarning(),
                    response.getNeeded(),
                    response.getProvided());
        } else {
            return "Couldn't call Slack API";
        }
    }

    @Nullable public String getError() {
        if (response != null) {
            return response.getError();
        }
        Throwable cause = getCause();
        return cause != null ? cause.getMessage() : null;
    }

    @Nullable public String getWarning() {
        return response != null ? response.getWarning() : null;
    }

    @Nullable public String getNeeded() {
        return response != null ? response.getNeeded() : null;
    }

    @Nullable public String getProvided() {
        return response != null ? response.getProvided() : null;
    }
}
