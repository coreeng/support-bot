import { InputError } from '@backstage/errors';
import express from 'express';
import Router from 'express-promise-router';
import { TicketsService } from './services/TicketsService/types';

export async function createRouter({
  ticketService,
}: {
  ticketService: TicketsService;
}): Promise<express.Router> {
  const router = Router();
  router.use(express.json());

  router.get('/tickets', async (_req, res) => {
    res.json(await ticketService.listTickets());
  });

  router.get('/ticket/:id', async (req, res) => {
    res.json(await ticketService.getTicket({ id: req.params.id }));
  });

  router.get('/teams', async (_req, res) => {
    res.json(await ticketService.getTeams());
  });

  router.get('/escalations', async (_req, res) => {
    res.json(await ticketService.getEscalations());
  });

  router.get('/escalation/:id', async (req, res) => {
    res.json(await ticketService.getEscalation({ id: req.params.id }));
  });

  router.get('/user', async (req, res) => {
    const email = req.query.email as string;
    if (!email) {
      throw new InputError('Email query parameter must be provided');
    }
    res.json(await ticketService.getUserTeams({ email }));
  });

  router.get('/stats', async (_req, res) => {
    // console.log(`GET /stats::::::: WHOOPITY!!!`);
    // const from = req.body[0]["from"] as string;
    // const to = req.body[0]["to"] as string;
    res.json(await ticketService.getStats());
  });

  return router;
}
