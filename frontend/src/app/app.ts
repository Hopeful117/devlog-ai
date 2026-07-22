import { Component } from '@angular/core';

import { AppLayout } from './core/layout/app-layout';

@Component({
  selector: 'app-root',
  imports: [AppLayout],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {}
