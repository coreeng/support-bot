import {
  coreServices,
  createBackendPlugin,
} from '@backstage/backend-plugin-api';
import { createRouter } from './router';
import { catalogServiceRef } from '@backstage/plugin-catalog-node/alpha';
import { createTicketsService } from './services/TicketsService/createTicketsService';

/**
 * slackbotApiPlugin backend plugin
 *
 * @public
 */
export const slackbotApiPlugin = createBackendPlugin({
  pluginId: 'slackbot-api',
  register(env) {
    env.registerInit({
      deps: {
        logger: coreServices.logger,
        httpRouter: coreServices.httpRouter
      },
      async init({ logger, httpRouter }) {
        const ticketService = await createTicketsService({
          logger
        });

        const router = await createRouter({ ticketService });
        httpRouter.use(router);

        // todo: remove below. For dev purposes only.
        const routes = [...new Set(router.stack
                                    .filter(layer => layer.route)
                                    .map(layer => layer.route.path))];
        
        for (let endpoint of routes) {
          httpRouter.addAuthPolicy({
            path: endpoint,
            allow: 'unauthenticated',
          });
        }
        // todo: remove above. For dev purposes only.
      }
    });
  },
});
