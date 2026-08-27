import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs/operators';

export interface Crumb {
  label: string;
  path: string | null;
}

/**
 * Where you are, and the way back.
 *
 * Read from the route table rather than pieced together from the URL: the
 * section a screen belongs to is a fact about the application, not something
 * to be inferred from its path, and two screens under the same menu do not
 * necessarily share a URL prefix.
 *
 * The last crumb is where you already are, so it is text rather than a link —
 * offering to navigate somewhere you are standing is noise.
 */
@Component({
  selector: 'app-breadcrumbs',
  imports: [RouterLink],
  templateUrl: './breadcrumbs.html',
  styleUrl: './breadcrumbs.css',
})
export class Breadcrumbs {

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly crumbs = signal<Crumb[]>([]);

  constructor() {

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => this.rebuild());

    this.rebuild();
  }

  private rebuild(): void {

    let leaf = this.route;

    while (leaf.firstChild) {
      leaf = leaf.firstChild;
    }

    const data = leaf.snapshot.data;

    if (!data['title']) {
      this.crumbs.set([]);
      return;
    }

    const trail: Crumb[] = [{ label: 'Home', path: '/' }];

    if (data['section']) {
      trail.push({
        label: data['section'],
        path: data['sectionPath'] ?? null,
      });
    }

    /*
     * A detail screen is named by the thing it shows, not by its type: a
     * trail reading "Payments › Payment" tells the reader nothing they did
     * not already know.
     */
    const param = data['titleParam']
      ? leaf.snapshot.paramMap.get(data['titleParam'])
      : null;

    trail.push({ label: param ?? data['title'], path: null });

    this.crumbs.set(trail);
  }
}
