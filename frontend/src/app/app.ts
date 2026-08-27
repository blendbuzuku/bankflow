import { Component, HostListener, inject, signal } from '@angular/core';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter } from 'rxjs/operators';

import { AuthService, UserResponse } from './core/services/auth';
import { ToastStack } from './shared/toast-stack';
import { Breadcrumbs } from './shared/breadcrumbs';
import { ConfirmDialog } from './shared/confirm-dialog';
import { TransactionService } from './core/services/transaction';

/**
 * The application shell.
 *
 * The navigation is role-aware: a customer sees their own banking, staff see
 * the payment tools. This is presentation only — every endpoint behind these
 * links is guarded on the server, so hiding a link is a convenience and never
 * the control itself.
 */
@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastStack, Breadcrumbs, ConfirmDialog],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {

  private readonly authService = inject(AuthService);
  private readonly transactionService = inject(TransactionService);
  private readonly router = inject(Router);

  readonly user = signal<UserResponse | null>(null);
  readonly pendingCount = signal(0);
  readonly recallCount = signal(0);

  /**
   * Which menu is open, by name.
   *
   * One at a time: two panels open at once overlap and neither is readable.
   */
  readonly openMenu = signal<string | null>(null);

  /** Login and registration stand alone, without the shell around them. */
  readonly showChrome = signal(false);

  constructor() {

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => this.onNavigated());

    this.onNavigated();
  }

  private onNavigated(): void {

    // Arriving somewhere is the end of choosing where to go.
    this.openMenu.set(null);

    const url = this.router.url;
    const authScreen = url.startsWith('/login') || url.startsWith('/register');

    this.showChrome.set(!authScreen && this.authService.isAuthenticated());

    if (authScreen || !this.authService.isAuthenticated()) {
      this.user.set(null);
      this.pendingCount.set(0);
      this.recallCount.set(0);
      return;
    }

    if (!this.authService.currentUser()) {

      this.authService.loadCurrentUser().subscribe({
        next: user => {
          this.user.set(user);
          this.refreshPendingCount();
        },
        error: () => this.user.set(null),
      });

      return;
    }

    this.user.set(this.authService.currentUser());
    this.refreshPendingCount();
  }

  /**
   * The badge is a prompt, not a control. A failure to read it should not
   * interrupt whatever the user is actually doing.
   */
  private refreshPendingCount(): void {

    if (!this.isStaff()) {
      this.pendingCount.set(0);
      this.recallCount.set(0);
      return;
    }

    this.transactionService.awaitingApproval().subscribe({
      next: pending => this.pendingCount.set(pending.length),
      error: () => this.pendingCount.set(0),
    });

    this.transactionService.openRecalls().subscribe({
      next: recalls => this.recallCount.set(recalls.length),
      error: () => this.recallCount.set(0),
    });
  }

  isStaff(): boolean {
    return this.authService.isStaff();
  }

  /** Supervisors and above: the checker half of maker-checker. */
  canApprove(): boolean {
    return this.authService.canApprove();
  }

  toggleMenu(name: string, event: MouseEvent): void {

    // Without this the document listener below closes it again immediately.
    event.stopPropagation();

    this.openMenu.set(this.openMenu() === name ? null : name);
  }

  /** Anywhere outside a menu is a decision not to use it. */
  @HostListener('document:click')
  closeMenus(): void {
    this.openMenu.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.openMenu.set(null);
  }

  /**
   * What a parent shows when its children are hidden.
   *
   * A count that only appears once the menu is open is a count nobody sees,
   * so anything waiting is surfaced on the parent instead.
   */
  paymentsBadge(): number {
    return this.pendingCount() + this.recallCount();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
