import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { liveQuery } from 'dexie';
import { AsyncPipe, DecimalPipe } from '@angular/common';
import { sgpDb } from '../../db/sgp-db';
import { StorageService } from '../../services/storage.service';
import { TranslocoPipe } from '@jsverse/transloco';
@Component({
  selector: 'app-diagnostics-page',
  standalone: true,
  imports: [AsyncPipe, DecimalPipe, TranslocoPipe],
  templateUrl: './diagnostics.page.html',
  styleUrl: './diagnostics.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DiagnosticsPage {
  readonly storage = inject(StorageService);
  readonly pending$ = liveQuery(() => sgpDb.cola_sync.count());
  readonly estimate = signal<StorageEstimate>({});
  async persist(): Promise<void> {
    await this.storage.requestPersistentStorage();
    this.estimate.set(await this.storage.usage());
  }
  async refresh(): Promise<void> {
    this.estimate.set(await this.storage.usage());
  }
}
