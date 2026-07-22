import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_ENVIRONMENT } from '../../core/config/app-environment';
import { IntentDefinition } from './analysis.models';

@Injectable({ providedIn: 'root' })
export class IntentCatalogService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(APP_ENVIRONMENT).backendBaseUrl}/api/v1`;

  getSupportedIntents(): Observable<readonly IntentDefinition[]> {
    return this.http.get<readonly IntentDefinition[]>(`${this.baseUrl}/intents`);
  }
}
