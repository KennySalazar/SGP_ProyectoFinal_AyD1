import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { AuthStore } from '../../../../core/services/auth.store';
import { TranslocoPipe } from '@jsverse/transloco';
import { UsuarioApiService } from '../../services/usuario-api.service';

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
  private readonly usuarioApi = inject(UsuarioApiService);
  readonly auth = inject(AuthStore);
  readonly challengeId = signal<string | null>(null);
  readonly message = signal<string | null>(null);
  readonly requestForm = this.fb.nonNullable.group({ currentPassword: ['', Validators.required] });
  readonly verifyForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  request(): void {
    if (this.requestForm.invalid) return;
    this.usuarioApi
      .requestTwoFactorChange(
        this.auth.user()?.twoFactorEnabled ?? false,
        this.requestForm.controls.currentPassword.value,
      )
      .subscribe((response) => this.challengeId.set(response.challengeId));
  }

  confirm(): void {
    const challengeId = this.challengeId();
    if (!challengeId || this.verifyForm.invalid) return;
    this.usuarioApi
      .confirmTwoFactorChange(
        this.auth.user()?.twoFactorEnabled ?? false,
        challengeId,
        this.verifyForm.controls.code.value,
      )
      .subscribe((response) => {
        this.message.set(response.message);
        this.challengeId.set(null);
        this.auth.loadMe().subscribe();
      });
  }
}
