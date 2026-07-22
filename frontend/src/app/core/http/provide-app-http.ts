import { EnvironmentProviders } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';

export function provideAppHttp(): EnvironmentProviders {
  return provideHttpClient();
}
