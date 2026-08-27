import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { Observable, of } from 'rxjs';

import { Confirm } from '../services/confirm';

/**
 * A screen that can lose work if it is left.
 *
 * Implemented by the component itself rather than watched from outside,
 * because only the component knows the difference between a form somebody has
 * filled in and one that merely exists.
 */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * Stops a half-filled form being lost to a stray click.
 *
 * Only asks when there is something to lose. A guard that questions every
 * navigation teaches people to dismiss it without reading, which is worse
 * than not having one.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = component => {

  if (!component?.hasUnsavedChanges?.()) {
    return true;
  }

  return inject(Confirm).ask({
    title: 'Leave without finishing?',
    message: 'What you have entered on this screen has not been submitted and '
      + 'will be lost.',
    confirmLabel: 'Leave and discard',
    cancelLabel: 'Stay here',
    danger: true,
  }) as Observable<boolean>;
};
