import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { Toasts } from '../../../core/services/toasts';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrls: ['../login/login.css', './register.css'],
})
export class Register {

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);

  username = '';
  email = '';
  password = '';
  confirmPassword = '';

  /*
   * Signals, not plain fields, for the same reason as the sign-in form: with
   * no zone, a field written from an HTTP callback leaves the button stuck on
   * "Creating account..." until an unrelated click forces a check.
   */
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly loading = signal(false);

  register(): void {

    this.errorMessage.set('');
    this.successMessage.set('');

    if (
      !this.username ||
      !this.email ||
      !this.password ||
      !this.confirmPassword
    ) {
      this.errorMessage.set('Please fill in all fields.');
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Passwords do not match.');
      return;
    }

    if (this.password.length < 8) {
      this.errorMessage.set('Password must be at least 8 characters.');
      return;
    }

    this.loading.set(true);

    this.authService.register({
      username: this.username,
      email: this.email,
      password: this.password,
    }).subscribe({
      next: () => {
        this.loading.set(false);
        this.successMessage.set('Account created successfully!');
        this.toasts.success(
          'Account created',
          'Sign in, then tell us your details so the branch can approve you.',
        );

        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 1000);
      },

      error: (error: any) => {
        this.loading.set(false);

        console.error('Registration failed:', error);

        this.errorMessage.set(
          error?.error?.message
            || 'Registration failed. Please try again.',
        );
      },
    });
  }
}