import { inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../services/auth';

/**
 * Role-based routing.
 *
 * Presentation only — every endpoint behind these routes is guarded on the
 * server. What these do is send people somewhere that makes sense for who they
 * are, rather than showing a teller a "register as a bank client" form or a
 * customer a screen of 403s.
 *
 * The role test is handed the service rather than reaching for it with
 * inject(): on a direct navigation the profile is not loaded yet, so the test
 * runs after an HTTP round trip, by which point the injection context the
 * guard started in is gone and inject() throws.
 */
function decideWith(
  check: (authService: AuthService) => boolean,
  fallback: string,
): CanActivateFn {

  return () => {

    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }

    const decide = () =>
      check(authService) ? true : router.createUrlTree([fallback]);

    if (authService.currentUser()) {
      return decide();
    }

    return authService.loadCurrentUser().pipe(
      map(decide),
      catchError((error: HttpErrorResponse) => {

        // Only a rejected session ends the session. A request the router
        // cancelled mid-navigation is not a reason to sign anyone out.
        if (error.status === 401) {
          authService.logout();
        }

        return of(router.createUrlTree(['/login']));
      }),
    );
  };
}

/** Bank staff only. Customers are sent to their own banking. */
export const staffGuard: CanActivateFn =
  decideWith(authService => authService.isStaff(), '/dashboard');

/**
 * Supervisors and above.
 *
 * Closing the books and releasing a parked payment are the checker half of
 * maker-checker, so a teller is deliberately not one of these. Sent to the
 * overview rather than to a screen that would answer 403 to every call.
 */
export const supervisorGuard: CanActivateFn =
  decideWith(authService => authService.canApprove(), '/overview');

/**
 * Customers only.
 *
 * Staff have no accounts of their own here, so the customer dashboard would be
 * an empty screen offering to register them as a bank client — which is not
 * what a teller is.
 */
export const customerGuard: CanActivateFn =
  decideWith(authService => !authService.isStaff(), '/overview');

/** Sends people to the right home for their role. */
export const landingGuard: CanActivateFn = () => {

  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  const home = () =>
    router.createUrlTree([authService.isStaff() ? '/overview' : '/dashboard']);

  if (authService.currentUser()) {
    return home();
  }

  return authService.loadCurrentUser().pipe(
    map(home),
    catchError((error: HttpErrorResponse) => {

      if (error.status === 401) {
        authService.logout();
      }

      return of(router.createUrlTree(['/login']));
    }),
  );
};
