import React from 'react';
import { Ticket } from '../../models/ticket';
import { EscalationTableComponent } from '../../components/EscalationTableComponent/EscalationTableComponent';
import { Escalation } from '../../models/escalation';
import { Team } from '../../models/team';

type EscalationsPageProps = {
    tickets: Ticket[];
    teams: Team[]
    escalations: Escalation[];
};

export const EscalationsPage = (props: EscalationsPageProps) => {
    return (
        <div>
            <EscalationTableComponent escalations={props.escalations} tickets={props.tickets} teams={props.teams} filters={{}} />
        </div>
    )
}
