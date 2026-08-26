import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { Toasts } from '../../../core/services/toasts';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(Toasts);

  username = '';
  email = '';
  password = '';
  confirmPassword = '';

  errorMessage = '';
  successMessage = '';
  loading = false;

  register(): void {

    this.errorMessage = '';
    this.successMessage = '';

    if (
      !this.username ||
      !this.email ||
      !this.password ||
      !this.confirmPassword
    ) {
      this.errorMessage = 'Please fill in all fields.';
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Passwords do not match.';
      return;
    }

    if (this.password.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters.';
      return;
    }

    this.loading = true;

    this.authService.register({
      username: this.username,
      email: this.email,
      password: this.password,
    }).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Account created successfully!';
        this.toasts.success(
          'Account created',
          'Sign in, then tell us your details so the branch can approve you.',
        );

        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 1000);
      },

      error: (error: any) => {
        this.loading = false;

        console.error('Registration failed:', error);

        this.errorMessage =
          error?.error?.message ||
          'Registration failed. Please try again.';
      },
    });
  }
}