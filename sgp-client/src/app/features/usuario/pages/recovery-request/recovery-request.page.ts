import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageResponse } from '../../../../core/models/auth.models';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';

@Component({
  selector: 'app-recovery-request-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AuthCardComponent, ButtonModule, InputTextModule],
  templateUrl: './recovery-request.page.html',
  styleUrl: './recovery-request.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RecoveryRequestPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  readonly sent = signal(false);
  readonly form = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });

  submit(): void {
    if (this.form.invalid) return;
    this.http.post<MessageResponse>('/api/v1/auth/password-recovery', this.form.getRawValue()).subscribe(() => this.sent.set(true));
  }
}
