import React from 'react';
import { Tabs, Tab } from '@material-ui/core';
import { NavLink } from 'react-router-dom';

export const tabs = [
    { label: 'Home', path: '', value: 'home' },
    { label: 'Tickets', path: 'tickets', value: 'tickets' },
    { label: 'Escalations', path: 'escalations', value: 'escalations' },
    { label: 'Health & Stats', path: 'health-and-stats', value: 'healthAndStats' },
];

export const getCurrentTab = (pathname: string): string => {
    // all tabs except the frist one
    const otherTabs = [...tabs.slice(1)];
    const match = otherTabs.find((tab) => pathname.endsWith(tab.path));
    return match ? match.value : 'home';
};

export const NavigationTabs = ({ currentTab, isLeadershipUser }: { currentTab: string, isLeadershipUser: boolean }) => (
  <Tabs value={currentTab}>
    {tabs.filter(t => {
      if (t.value === 'healthAndStats' && !isLeadershipUser) {
        return false;
      }
      return true;
    }).map((tab) => (
      <Tab key={tab.value} label={tab.label} component={NavLink} to={tab.path} value={tab.value} />
    ))}
  </Tabs>
);
