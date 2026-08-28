import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/services/auth';
import { Toasts } from '../../../core/services/toasts';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);

  username = '';
  password = '';

  /*
   * Signals, not plain fields. There is no zone here, so a field set from
   * inside an HTTP callback never marks this component dirty -- the button
   * would sit on its busy label after a rejected password until some
   * unrelated click happened to force a check.
   */
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  login(): void {

    if (!this.username || !this.password) {
      this.errorMessage.set('Username and password are required.');
      this.toasts.error('Username and password are required.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.login(
      this.username,
      this.password
    ).subscribe({

      next: () => {

        /*
         * Load the profile before routing: where someone belongs depends on
         * their role, and loadCurrentUser caches it on the service so the
         * guard on the far side does not fetch it a second time.
         */
        this.authService.loadCurrentUser().subscribe({

          next: user => {
            this.loading.set(false);

            // A failed attempt is no longer news once you are in.
            this.toasts.clear();

            this.toasts.success(
              `Signed in as ${user.username}`,
              `You are working as ${user.role.toLowerCase().replace('_', ' ')}.`,
            );

            this.router.navigate([
              this.authService.isStaff() ? '/overview' : '/dashboard',
            ]);
          },

          error: () => {
            this.loading.set(false);
            this.authService.logout();
            this.errorMessage.set('Unable to load your user profile.');
            this.toasts.error(
              'Signed in, but your profile could not be loaded.',
              'Please try again.',
            );
          }
        });
      },

      error: error => {

        this.loading.set(false);

        if (error.status === 401) {
          this.errorMessage.set('Invalid username or password.');
          this.toasts.error(
            'Could not sign you in',
            'That username and password do not match an account.',
          );
        } else {
          this.errorMessage.set('Unable to connect to BankFlow.');
          this.toasts.error(
            'BankFlow is not responding',
            'The service could not be reached. Check it is running.',
          );
        }
      }
    });
  }
}