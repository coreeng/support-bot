import React, { useState } from 'react';
import { Header, HeaderLabel } from '@backstage/core-components';
import { MenuItem, FormControl, Select, InputLabel } from '@material-ui/core';
import { User } from '../../models/user';
import { tenantUsers } from '../../models/data/example_users';

type AppHeaderProps = {
  currentUser: User;
  onUserChange: (user: User) => void;
};

export const AppHeader = ({ currentUser, onUserChange }: AppHeaderProps) => {
  const allUsers = [...tenantUsers];
  const [selectedUser, setSelectedUser] = useState(currentUser);

  const handleUserChange = (event: React.ChangeEvent<{ value: unknown }>) => {
    const newUser = allUsers.find(user => user.name === event.target.value);
    if (newUser) {
      setSelectedUser(newUser);
      onUserChange({ ...newUser, teams: [] });
    }
  };

  return (
    <Header title="Welcome to CECG Support" subtitle="Browse tickets from slackbot usage">
      <FormControl variant="outlined" size="small" style={{ minWidth: 200, marginRight: '1rem' }}>
        <InputLabel>User</InputLabel>
        <Select value={selectedUser.name} onChange={handleUserChange} label="User">
          {allUsers.map(user => (
            <MenuItem key={user.name} value={user.name}>
              {user.name}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
      <HeaderLabel label="Owner" value="CECG" />
      <HeaderLabel label="Lifecycle" value="Alpha" />
    </Header>
  );
};
