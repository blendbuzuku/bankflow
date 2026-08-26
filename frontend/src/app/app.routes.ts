import { Routes } from '@angular/router';

import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { Dashboard } from './features/dashboard/dashboard';
import { PaymentForm } from './features/payments/payment-form';
import { PaymentDetail } from './features/payments/payment-detail';
import { ApprovalQueue } from './features/payments/approval-queue';
import { Overview } from './features/operations/overview';
import { Clients } from './features/operations/clients';
import { EndOfDay } from './features/operations/end-of-day';

import {
  customerGuard,
  landingGuard,
  staffGuard,
  supervisorGuard,
} from './core/guards/role-guards';

export const routes: Routes = [

  /*
   * Where you land depends on who you are: staff get their work queue,
   * customers get their own banking.
   */
  { path: '', canActivate: [landingGuard], children: [] },

  { path: 'login', component: Login },
  { path: 'register', component: Register },

  // --- customer ---

  {
    path: 'dashboard',
    component: Dashboard,
    canActivate: [customerGuard],
  },

  // --- staff ---

  {
    path: 'overview',
    component: Overview,
    canActivate: [staffGuard],
  },

  {
    path: 'clients',
    component: Clients,
    canActivate: [staffGuard],
  },

  /*
   * The literal path comes first so /payments/new is never read as a
   * transaction reference.
   */
  {
    path: 'payments/new',
    component: PaymentForm,
    canActivate: [staffGuard],
  },

  {
    path: 'payments/:reference',
    component: PaymentDetail,
    canActivate: [staffGuard],
  },

  {
    path: 'approvals',
    component: ApprovalQueue,
    canActivate: [staffGuard],
  },

  // Closing the books is a supervisor's job, not a teller's.
  {
    path: 'end-of-day',
    component: EndOfDay,
    canActivate: [supervisorGuard],
  },

  { path: '**', redirectTo: '' },
];
