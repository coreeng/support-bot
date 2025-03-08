import { createRouteRef, createSubRouteRef } from '@backstage/core-plugin-api';

export const rootRouteRef = createRouteRef({
  id: 'cecg-support-bot',
});

export const ticketRouteRef = createSubRouteRef({
  id: 'tickets',
  parent: rootRouteRef,
  path: '/tickets',
});

export const escalationRouteRef = createSubRouteRef({
  id: 'escalations',
  parent: rootRouteRef,
  path: '/escalations',
});

export const healthAndStatsRouteRef = createSubRouteRef({
  id: 'health-and-stats',
  parent: rootRouteRef,
  path: '/health-and-stats',
});
