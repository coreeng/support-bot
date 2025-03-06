import React from 'react';
import { createDevApp } from '@backstage/dev-utils';
import { cecgSupportBotPlugin, CecgSupportBotPage } from '../src/plugin';

createDevApp()
  .registerPlugin(cecgSupportBotPlugin)
  .addPage({
    element: <CecgSupportBotPage />,
    title: 'Root Page',
    path: '/cecg-support-bot',
  })
  .render();
