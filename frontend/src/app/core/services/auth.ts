import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, finalize, of, shareReplay, tap } from 'rxjs';

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: 'BANK_ADMIN' | 'OPERATIONS' | 'TELLER' | 'CUSTOMER';
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED';
  createdAt: string;
  updatedAt: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {

  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/auth';

  login(
    username: string,
    password: string
  ): Observable<LoginResponse> {

    return this.http
      .post<LoginResponse>(`${this.apiUrl}/login`, {
        username,
        password,
      })
      .pipe(
        tap((response) => {
          localStorage.setItem(
            'bankflow_token',
            response.token
          );
        })
      );
  }

  register(
    request: RegisterRequest
  ): Observable<UserResponse> {

    return this.http.post<UserResponse>(
      `${this.apiUrl}/register`,
      request
    );
  }

  getCurrentUser(): Observable<UserResponse> {

    return this.http.get<UserResponse>(
      `${this.apiUrl}/me`
    );
  }

  /**
   * The signed-in user, loaded once and shared.
   *
   * Held as a signal so the shell can decide what a role may see without every
   * screen refetching the same profile.
   */
  readonly currentUser = signal<UserResponse | null>(null);

  /**
   * The profile, fetched at most once.
   *
   * The shell and the route guard both want it on the same navigation. Without
   * sharing, that is two requests for the same thing, and whichever one the
   * router cancels surfaces as an error — which used to look like a failed
   * session. One in-flight request, replayed to every subscriber, removes the
   * race entirely.
   */
  loadCurrentUser(): Observable<UserResponse> {

    const known = this.currentUser();

    if (known) {
      return of(known);
    }

    this.inFlight ??= this.getCurrentUser().pipe(
      tap(user => this.currentUser.set(user)),
      finalize(() => this.inFlight = null),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    return this.inFlight;
  }

  private inFlight: Observable<UserResponse> | null = null;

  /**
   * Bank staff. Customers see only their own accounts and history; staff
   * operate payments.
   */
  isStaff(): boolean {

    const role = this.currentUser()?.role;

    return role === 'TELLER'
      || role === 'OPERATIONS'
      || role === 'BANK_ADMIN';
  }

  /** May release or decline a payment held for approval. */
  canApprove(): boolean {

    const role = this.currentUser()?.role;

    return role === 'OPERATIONS' || role === 'BANK_ADMIN';
  }

  logout(): void {
    localStorage.removeItem('bankflow_token');
    this.currentUser.set(null);
    this.inFlight = null;
  }

  getToken(): string | null {
    return localStorage.getItem('bankflow_token');
  }

  /**
   * Whether there is a session worth acting on.
   *
   * The expiry is read from the token, because a token that has run out is
   * indistinguishable from a valid one by presence alone — and treating it as
   * valid sends the guards into a redirect loop against a server that answers
   * 401 to everything. This is a usability check, not a security one: the
   * signature is verified server-side and nothing here can be trusted to
   * grant access.
   */
  isAuthenticated(): boolean {

    const expiry = this.tokenExpiry();

    return expiry !== null && expiry > Date.now();
  }

  private tokenExpiry(): number | null {

    const token = this.getToken();

    if (!token) {
      return null;
    }

    try {
      const payload = JSON.parse(
        atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')),
      );

      return typeof payload.exp === 'number' ? payload.exp * 1000 : null;

    } catch {
      // Not a token we can read, so not a session we can rely on.
      return null;
    }
  }
}