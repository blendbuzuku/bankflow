import { Routes } from '@angular/router';

import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { Dashboard } from './features/dashboard/dashboard';
import { PaymentForm } from './features/payments/payment-form';
import { PaymentDetail } from './features/payments/payment-detail';
import { ApprovalQueue } from './features/payments/approval-queue';
import { RecallQueue } from './features/payments/recall-queue';
import { MessageInspector } from './features/payments/message-inspector';
import { Statements } from './features/statements/statements';
import { Overview } from './features/operations/overview';
import { Clients } from './features/operations/clients';
import { EndOfDay } from './features/operations/end-of-day';
import { Banks } from './features/operations/banks';

import { authGuard } from './core/guards/auth-guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes';
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
    canDeactivate: [unsavedChangesGuard],
    data: { title: 'My banking' },
  },

  // --- staff ---

  {
    path: 'overview',
    component: Overview,
    canActivate: [staffGuard],
    data: { section: 'Banking', title: 'Overview' },
  },

  {
    path: 'clients',
    component: Clients,
    canActivate: [staffGuard],
    canDeactivate: [unsavedChangesGuard],
    data: {
      section: 'Banking', sectionPath: '/overview',
      title: 'Clients and accounts',
    },
  },

  /*
   * The literal path comes first so /payments/new is never read as a
   * transaction reference.
   */
  {
    path: 'payments/new',
    component: PaymentForm,
    canActivate: [staffGuard],
    canDeactivate: [unsavedChangesGuard],
    data: {
      section: 'Payments', sectionPath: '/payments/new',
      title: 'New payment',
    },
  },

  {
    path: 'payments/:reference',
    component: PaymentDetail,
    canActivate: [staffGuard],
    data: {
      section: 'Payments', sectionPath: '/payments/new',
      title: 'Payment', titleParam: 'reference',
    },
  },

  {
    path: 'approvals',
    component: ApprovalQueue,
    canActivate: [staffGuard],
    data: {
      section: 'Payments', sectionPath: '/payments/new',
      title: 'Approvals',
    },
  },

  /*
   * A teller can see the queue and raise a recall; only a supervisor answers
   * one, which the screen enforces per row rather than at the route.
   */
  {
    path: 'recalls',
    component: RecallQueue,
    canActivate: [staffGuard],
    data: {
      section: 'Payments', sectionPath: '/payments/new',
      title: 'Recalls',
    },
  },

  /*
   * Reading the traffic is how a scheme problem gets diagnosed, so any member
   * of staff can, but a message quotes both parties' business and so is not
   * a customer's to browse.
   */
  {
    path: 'messages',
    component: MessageInspector,
    canActivate: [staffGuard],
    canDeactivate: [unsavedChangesGuard],
    data: {
      section: 'Scheme', sectionPath: '/messages',
      title: 'Messages',
    },
  },

  /*
   * An entry in the directory decides where money goes, so maintaining it sits
   * with the same role that releases a payment.
   */
  {
    path: 'banks',
    component: Banks,
    canActivate: [supervisorGuard],
    canDeactivate: [unsavedChangesGuard],
    data: {
      section: 'Scheme', sectionPath: '/messages',
      title: 'Bank directory',
    },
  },

  // Closing the books is a supervisor's job, not a teller's.
  {
    path: 'end-of-day',
    component: EndOfDay,
    canActivate: [supervisorGuard],
    data: {
      section: 'Reporting', sectionPath: '/statements',
      title: 'End of day',
    },
  },

  /*
   * Both roles use this one: staff pick any account, a customer only their
   * own, and the server enforces the difference either way.
   */
  {
    path: 'statements',
    component: Statements,
    canActivate: [authGuard],
    data: { section: 'Reporting', title: 'Statements' },
  },

  { path: '**', redirectTo: '' },
];
