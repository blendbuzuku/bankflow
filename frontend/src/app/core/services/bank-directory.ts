import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CorrespondentBank {
  id: number;
  bic: string;
  name: string;
  country: string;

  /** "Name — BIC", which is how it reads in a dropdown. */
  label: string;

  active: boolean;
}

/**
 * The banks this bank can pay.
 *
 * The selectable list is readable by anyone signed in, because it is what
 * fills the dropdown on a payment form. Everything that changes it is
 * restricted server-side to operations.
 */
@Injectable({ providedIn: 'root' })
export class BankDirectoryService {

  private readonly http = inject(HttpClient);

  /** Banks that may be chosen for a payment. */
  selectable(): Observable<CorrespondentBank[]> {
    return this.http.get<CorrespondentBank[]>('/api/banks');
  }

  /** Everything, retired entries included. */
  all(): Observable<CorrespondentBank[]> {
    return this.http.get<CorrespondentBank[]>('/api/banks/all');
  }

  add(bic: string, name: string, country: string):
    Observable<CorrespondentBank> {

    return this.http.post<CorrespondentBank>('/api/banks', {
      bic, name, country,
    });
  }

  rename(id: number, name: string): Observable<CorrespondentBank> {
    return this.http.put<CorrespondentBank>(`/api/banks/${id}`, { name });
  }

  retire(id: number): Observable<CorrespondentBank> {
    return this.http.post<CorrespondentBank>(`/api/banks/${id}/retire`, {});
  }

  reinstate(id: number): Observable<CorrespondentBank> {
    return this.http.post<CorrespondentBank>(`/api/banks/${id}/reinstate`, {});
  }
}
