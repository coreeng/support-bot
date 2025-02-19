import { createApiFactory, createPlugin, createRoutableExtension, discoveryApiRef, identityApiRef } from '@backstage/core-plugin-api';
import { rootRouteRef, ticketRouteRef, escalationRouteRef, healthAndStatsRouteRef } from './routes';
import { supportBotApiRef, SupportBotApi } from './api/SupportBotApi';

export const cecgSupportBotPlugin = createPlugin({
  id: 'cecg-support-bot',
  routes: {
    root: rootRouteRef,
    tickets: ticketRouteRef,
    escalations: escalationRouteRef,
    healthAndStats: healthAndStatsRouteRef,
  },
  apis: [
    createApiFactory({
      api: supportBotApiRef,
      deps: { discoveryApi: discoveryApiRef, identityApi: identityApiRef },
      factory: ({ discoveryApi, identityApi }) => new SupportBotApi({ discoveryApi, identityApi })
    }),
  ],
});

export const CecgSupportBotPage = cecgSupportBotPlugin.provide(
  createRoutableExtension({
    name: 'CecgSupportBotPage',
    component: () =>
      import('./CECGSupportBot').then(m => m.CECGSupportBot),
    mountPoint: rootRouteRef,
  }),
);
