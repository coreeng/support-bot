export interface SentimentCounts {
    positives: number;
    neutrals: number;
    negatives: number;
}

export interface SentimentSummaryValue {
    date: string;
    authorSentiments: SentimentCounts;
    supportSentiments: SentimentCounts;
    othersSentiments: SentimentCounts;
}

export interface SentimentSummary {
    type: string;
    from: string;
    to: string;
    values: SentimentSummaryValue[];
}
