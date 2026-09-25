import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageResponse } from '../../../../core/models/auth.models';
import { AuthCardComponent } from '../../../../shared/components/auth-card/auth-card.component';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-recovery-reset-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthCardComponent,
    ButtonModule,
    InputTextModule,
    TranslocoPipe,
  ],
  templateUrl: './recovery-reset.page.html',
  styleUrl: './recovery-reset.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecoveryResetPage {
  private readonly fb = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly done = signal(false);
  readonly form = this.fb.nonNullable.group({
    email: [
      this.route.snapshot.queryParamMap.get('email') ?? '',
      [Validators.required, Validators.email],
    ],
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(10),
        Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
      ],
    ],
  });

  submit(): void {
    if (this.form.invalid) return;
    this.http
      .post<MessageResponse>('/api/v1/auth/password-recovery/verify', this.form.getRawValue())
      .subscribe(() => this.done.set(true));
  }
}
