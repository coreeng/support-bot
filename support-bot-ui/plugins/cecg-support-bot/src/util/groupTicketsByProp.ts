// groupTicketsByProp.ts
import {Ticket} from '../models/ticket';

/** 
 * Groups tickets by a string property (e.g., 'impact', 'client', etc.)
 * Returns an array of objects suitable for a Recharts <BarChart> or <PieChart>:
 *   [ { name: <propertyValue>, count: number }, ... ]
 */
export function groupTicketsByProp(tickets: Ticket[], prop: keyof Ticket) {
  // Build a map of propValue -> count
  const counts: Record<string, number> = {};

  for (const ticket of tickets) {
    const key = String(ticket[prop]); // e.g. "Breaking BAU"
    counts[key] = (counts[key] || 0) + 1;
  }

  // Turn that map into an array sorted by descending count
  return Object.entries(counts)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);
}
