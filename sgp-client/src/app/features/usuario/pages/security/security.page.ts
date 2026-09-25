import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { AuthStore } from '../../../../core/services/auth.store';
import { ChallengeResponse, MessageResponse } from '../../../../core/models/auth.models';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-security-page',
  standalone: true,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, TagModule, TranslocoPipe],
  templateUrl: './security.page.html',
  styleUrl: './security.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SecurityPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  readonly auth = inject(AuthStore);
  readonly challengeId = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly requestForm = this.fb.nonNullable.group({ currentPassword: ['', Validators.required] });
  readonly verifyForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  request(): void {
    if (this.requestForm.invalid) return;
    const endpoint = this.auth.user()?.twoFactorEnabled
      ? '/api/v1/auth/2fa/disable'
      : '/api/v1/auth/2fa/enable';
    this.http
      .post<ChallengeResponse>(endpoint, this.requestForm.getRawValue())
      .subscribe((response) => this.challengeId.set(response.challengeId));
  }

  confirm(): void {
    const challengeId = this.challengeId();
    if (!challengeId || this.verifyForm.invalid) return;
    const endpoint = this.auth.user()?.twoFactorEnabled
      ? '/api/v1/auth/2fa/disable/verify'
      : '/api/v1/auth/2fa/enable/verify';
    this.http
      .post<MessageResponse>(endpoint, { challengeId, otp: this.verifyForm.controls.code.value })
      .subscribe((response) => {
        this.message.set(response.message);
        this.challengeId.set(null);
        this.auth.loadMe().subscribe();
      });
  }
}
