/**
 * A link to a Slack message built from its channel and timestamp alone. Slack resolves it to the
 * viewer's workspace, so no API call (and no workspace URL) is needed to render it.
 */
export function slackDeepLink(channelId: string, messageTs: string): string {
  return `https://slack.com/archives/${channelId}/p${messageTs.replace(".", "")}`;
}
