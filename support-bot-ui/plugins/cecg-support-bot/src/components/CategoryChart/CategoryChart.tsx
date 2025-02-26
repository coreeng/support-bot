// CategoryChart.tsx
import React, { useState } from 'react';
import { Ticket } from '../../models/ticket';
import { InfoCard } from '@backstage/core-components';
import {
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@material-ui/core';
import { BarChart, Bar, Cell, XAxis, YAxis, Tooltip, Legend, CartesianGrid, ResponsiveContainer } from 'recharts';
import { groupTicketsByProp } from '../../util/groupTicketsByProp';

type CategoryChartProps = {
  tickets: Ticket[];
};

// Add whichever fields you want your user to pick from:
const categoryOptions = [
    { label: 'Status', value: 'status' },
    { label: 'Impact', value: 'impact' },
];

// A color map for "impact" categories:
const COLORS: Record<string, string> = {
    'Abnormal Behaviour': '#FFDC00', // bright yellow
    'Breaking BAU': '#FF851B',       // orange
    'Breaking Prod': '#FF4136',      // red
    'open': '#FFDC00', // yellow
    'resolved': '#2ECC40', // green
    'escalated': '#FF4136', // red
};

  
// A default fallback color if a category doesn’t match:
const DEFAULT_COLOR = '#8884d8';


export function CategoryChart({ tickets }: CategoryChartProps) {
  // The user’s chosen category field. Default to 'impact'.
  const [categoryField, setCategoryField] = useState<'impact' | 'status'>('impact');

  // Now group your tickets by the chosen field
  const chartData = groupTicketsByProp(tickets, categoryField);

  return (
    <InfoCard
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6" style={{ marginRight: 16 }}>
            Tickets by {categoryField}
          </Typography>

          <FormControl variant="outlined" size="small">
            <InputLabel>Group By</InputLabel>
            <Select
              value={categoryField}
              onChange={e => setCategoryField(e.target.value as 'impact' | 'status')}
              label="Group By"
            >
              {categoryOptions.map(opt => (
                <MenuItem key={opt.value} value={opt.value as any}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </div>
      }
    >
      <div style={{ width: '100%', height: 250 }}>
        <ResponsiveContainer>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis allowDecimals={false} />
            <Tooltip />
            <Legend />
            <Bar dataKey="count" fill="#8884d8">
                {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[entry.name] || DEFAULT_COLOR} />
                ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </InfoCard>
  );
}
