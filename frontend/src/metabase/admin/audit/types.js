/* @flow */

import type { Card } from "metabase/meta/types/Card";

export type AuditDashCard = {
  card: Card,
  series?: Card[],
};

export type AuditCardPosition = {
  x: number,
  y: number,
  w: number,
  h: number,
};

export type AuditCard = [AuditCardPosition, AuditDashCard];
