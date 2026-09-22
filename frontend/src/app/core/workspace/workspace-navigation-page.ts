import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { WorkspaceResourceResolver } from './resource-resolver';

@Component({
  selector: 'app-workspace-navigation-page',
  imports: [RouterLink],
  templateUrl: './workspace-navigation-page.html',
  styleUrl: './workspace-navigation-page.scss',
})
export class WorkspaceNavigationPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly resolver = inject(WorkspaceResourceResolver);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const resolution = this.resolver.resolve(this.route.snapshot.queryParamMap.get('resource'));
    if (resolution.status === 'resolved') {
      void this.router.navigateByUrl(resolution.destination);
      return;
    }

    this.error.set(resolution.reason);
  }
}
