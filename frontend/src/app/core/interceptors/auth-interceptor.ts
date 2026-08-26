import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { Toasts } from '../services/toasts';

/**
 * Carries the session on every call, and ends it when the server says it is
 * over.
 *
 * A 401 means the token is gone or expired — every subsequent screen would
 * fail the same way, so the session is cleared and the user is told once why
 * they are back at sign-in, rather than being left on a page that quietly
 * refuses to load. A 403 is left alone: the session is fine, that particular
 * action simply is not theirs, and the screen that asked reports it — saying
 * so here as well would announce the same refusal twice.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {

  const router = inject(Router);
  const toasts = inject(Toasts);

  const token = localStorage.getItem('bankflow_token');

  const authorised = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorised).pipe(
    catchError((error: HttpErrorResponse) => {

      const onAuthScreen = router.url.startsWith('/login')
        || router.url.startsWith('/register');

      if (error.status === 401 && !onAuthScreen) {
        localStorage.removeItem('bankflow_token');
        toasts.error(
          'Your session has ended',
          'Please sign in again to continue.',
        );
        router.navigate(['/login']);
      }

      return throwError(() => error);
    }),
  );
};
