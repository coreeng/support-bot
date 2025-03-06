import React, { useState } from 'react';
import { Impact, Ticket } from '../../models/ticket';
import { TicketTableComponent } from '../../components/TicketTableComponent/TicketTableComponent';
import { useLocation, useNavigate } from 'react-router-dom';
import { Box, Chip, Typography } from '@material-ui/core';
import { Team } from '../../models/team';

type TicketsPageProps = {
    tickets: Ticket[];
    teams: Team[];
};

export const TicketsPage = (props: TicketsPageProps) => {
    const location = useLocation();
    const navigate = useNavigate();
    const query = new URLSearchParams(location.search);
    const teamParam = query.get('team');

    const [filters, setFilters] = useState<{ status?: string; team?: string; impact?: Impact }>({ team: (teamParam as string) || undefined });

    const toggleStatusFilter = (statusValue: string) => {
        setFilters(prev => {
            if (prev.status === statusValue) {
                // If already active, remove the filter
                const { status, ...other } = prev;
                return other;
            }
            return { ...prev, status: statusValue };
        });
    };

    const handleTableFilterChange = (newFilters: { status?: string; team?: string }) => {
        setFilters(newFilters);
        const urlFilters = [];
        if (newFilters.team) urlFilters.push(`team=${encodeURIComponent(newFilters.team)}`);
        if (newFilters.status) urlFilters.push(`status=${encodeURIComponent(newFilters.status)}`);
        if (urlFilters.length) navigate(`${location.pathname}?${urlFilters.join('&')}`);
    };

    let visibleTickets = props.tickets;

    if (filters.status) {
        visibleTickets = visibleTickets.filter(t => t.status === filters.status);
    }

    if (filters.team) {
        visibleTickets = visibleTickets.filter(t => t.team?.name === filters.team);
    }

    return (
        <>
            <Typography variant="h4">Quick Filters</Typography>
    
            {/* Quick Filter Chips */}
            <Box mb={2}>
                <Chip
                    label="Opened"
                    clickable
                    color={filters.status === 'opened' ? 'primary' : 'default'}
                    onClick={() => toggleStatusFilter('opened')}
                    style={{ marginRight: 8 }}
                />
                <Chip
                    label="Closed"
                    clickable
                    color={filters.status === 'closed' ? 'primary' : 'default'}
                    onClick={() => toggleStatusFilter('closed')}
                    style={{ marginRight: 8 }}
                />
                <Chip
                    label="Escalated"
                    clickable
                    color={filters.status === 'escalated' ? 'primary' : 'default'}
                    onClick={() => toggleStatusFilter('escalated')}
                    style={{ marginRight: 8 }}
                />
            </Box>
            <div>
                <TicketTableComponent
                    tickets={props.tickets}
                    teams={props.teams}
                    filters={filters}
                    onFilterChange={handleTableFilterChange}
                    />
            </div>
        </>
        
    )
}
