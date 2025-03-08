import React, { useState } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, Legend, CartesianGrid, ResponsiveContainer } from "recharts";
import { DateTime } from "luxon";
import {SentimentSummary, SentimentSummaryValue} from "../../models/sentiment-summary";
import { InfoCard } from "@backstage/core-components";
import { FormControl, MenuItem, Select, Typography } from "@material-ui/core";

type SentimentPerDay = {
    date: DateTime;
    author_negative: number,
    author_neutral: number,
    author_positive: number,
    support_negative: number,
    support_neutral: number,
    support_positive: number,
    others_negative: number,
    others_neutral: number,
    others_positive: number,
}

const aggregateDailyValues = (data: SentimentSummary, previousDays: number) => {
    const dateRelevantData = data.values.filter((value) => {
        const date = DateTime.fromISO(value.date);
        const now = DateTime.now();
        const diff = now.diff(date, "days").days;
        return diff <= previousDays;
    });

    const formattedData = dateRelevantData.reduce((acc, value) => {
        acc.author.negative += value.authorSentiments.negatives;
        acc.author.neutral += value.authorSentiments.neutrals;
        acc.author.positive += value.authorSentiments.positives;
        acc.support.negative += value.supportSentiments.negatives;
        acc.support.neutral += value.supportSentiments.neutrals;
        acc.support.positive += value.supportSentiments.positives;
        acc.others.negative += value.othersSentiments.negatives;
        acc.others.neutral += value.othersSentiments.neutrals;
        acc.others.positive += value.othersSentiments.positives;
        return acc;
    }, {
        author: { negative: 0, neutral: 0, positive: 0 },
        support: { negative: 0, neutral: 0, positive: 0 },
        others: { negative: 0, neutral: 0, positive: 0 }
    });

    return [
        {
            name: "author",
            negative: formattedData.author.negative,
            neutral: formattedData.author.neutral,
            positive: formattedData.author.positive
        },
        {
            name: "support",
            negative: formattedData.support.negative,
            neutral: formattedData.support.neutral,
            positive: formattedData.support.positive
        },
        {
            name: "others",
            negative: formattedData.others.negative,
            neutral: formattedData.others.neutral,
            positive: formattedData.others.positive
        }
    ];
}

const aggregateAllValues = (data: SentimentSummary): SentimentPerDay[] => {
    type GroupedSentiment = {
       [key: string]: SentimentPerDay
    }
    const groupedData = data.values.reduce((acc: GroupedSentiment, value: SentimentSummaryValue): GroupedSentiment => {
        const date = DateTime.fromISO(value.date);
        const dateKey = date.toFormat("yyyy-MM-dd");
        if (!acc[dateKey]) {
            acc[dateKey] = {
                date: date,
                author_negative: 0,
                author_neutral: 0,
                author_positive: 0,
                support_negative: 0,
                support_neutral: 0,
                support_positive: 0,
                others_negative: 0,
                others_neutral: 0,
                others_positive: 0,
            };
        }
        acc[dateKey].author_negative += value.authorSentiments.negatives;
        acc[dateKey].author_neutral += value.authorSentiments.neutrals;
        acc[dateKey].author_positive += value.authorSentiments.positives;
        acc[dateKey].support_negative += value.supportSentiments.negatives;
        acc[dateKey].support_neutral += value.supportSentiments.neutrals;
        acc[dateKey].support_positive += value.supportSentiments.positives;
        acc[dateKey].others_negative += value.othersSentiments.negatives;
        acc[dateKey].others_neutral += value.othersSentiments.neutrals;
        acc[dateKey].others_positive += value.othersSentiments.positives;
        return acc;
    }, {});
    return Object.values(groupedData).sort((a, b) => a.date.toMillis() - b.date.toMillis());
}


export const SentimentChart = ({ data }: { data: SentimentSummary }) => {
    const [dateRange, setDateRange] = useState(7);

    const chartData = dateRange === 0
        ? aggregateAllValues(data)
        : aggregateDailyValues(data, dateRange);

    return (
        <InfoCard title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h6" style={{ marginRight: 16 }}>
                Sentiments
              </Typography>
    
              <FormControl variant="outlined" size="small">
                <Select
                    value={dateRange}
                    onChange={(e) => setDateRange(Number(e.target.value))}
                    label="Date Range"
                >
                    <MenuItem value={7}>Last 7 Days</MenuItem>
                    <MenuItem value={14}>Last 14 Days</MenuItem>
                    <MenuItem value={30}>Last 30 Days</MenuItem>
                    <MenuItem value={0}>All Days</MenuItem>
                </Select>
              </FormControl>
            </div>
          }>
    
          <ResponsiveContainer width="100%" height={400}>
            <BarChart data={chartData} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey={dateRange === 0 ? "date" : "name"} />
              <YAxis />
              <Tooltip />
              <Legend />
              {dateRange === 0 ? (
                <>
                   <Bar dataKey="author_negative" stackId="author" fill="#F44336" />
                    <Bar dataKey="author_neutral" stackId="author" fill="#2196F3" />
                    <Bar dataKey="author_positive" stackId="author" fill="#4CAF50" />

                    <Bar dataKey="support_negative" stackId="support" fill="#F44356" />
                    <Bar dataKey="support_neutral" stackId="support" fill="#2196D3" />
                    <Bar dataKey="support_positive" stackId="support" fill="#4CAF30" />

                    <Bar dataKey="others_negative" stackId="others" fill="#F45336" />
                    <Bar dataKey="others_neutral" stackId="others" fill="#21A6F3" />
                    <Bar dataKey="others_positive" stackId="others" fill="#4CBF50" />
                </>
              ) : (
                <>
                    <Bar dataKey="negative" stackId="same" fill="#F44336" />
                    <Bar dataKey="neutral" stackId="same" fill="#2196F3" />
                    <Bar dataKey="positive" stackId="same" fill="#4CAF50" />
                </>
              )}
            </BarChart>
          </ResponsiveContainer>
        </InfoCard>
      );
};
