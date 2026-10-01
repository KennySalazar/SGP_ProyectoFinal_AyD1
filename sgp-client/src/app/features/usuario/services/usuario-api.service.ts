import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ChallengeResponse,
  LoginResponse,
  MessageResponse,
} from '../../../core/models/auth.models';

@Injectable({ providedIn: 'root' })
export class UsuarioApiService {
  private readonly http = inject(HttpClient);

  login(credentials: { email: string; password: string }): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', credentials, {
      withCredentials: true,
    });
  }

  verifyLogin(challengeId: string, otp: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      '/api/v1/auth/login/verify',
      { challengeId, otp },
      { withCredentials: true },
    );
  }

  register(data: { email: string; password: string }): Observable<ChallengeResponse> {
    return this.http.post<ChallengeResponse>('/api/v1/auth/register', data);
  }

  verifyRegistration(email: string, challengeId: string, otp: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/v1/auth/register/verify', {
      email,
      challengeId,
      otp,
    });
  }

  requestPasswordRecovery(data: { email: string }): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/v1/auth/password-recovery', data);
  }

  resetPassword(data: {
    email: string;
    otp: string;
    newPassword: string;
  }): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/v1/auth/password-recovery/verify', data);
  }

  requestTwoFactorChange(
    twoFactorEnabled: boolean,
    currentPassword: string,
  ): Observable<ChallengeResponse> {
    const endpoint = twoFactorEnabled ? '/api/v1/auth/2fa/disable' : '/api/v1/auth/2fa/enable';
    return this.http.post<ChallengeResponse>(endpoint, { currentPassword });
  }

  confirmTwoFactorChange(
    twoFactorEnabled: boolean,
    challengeId: string,
    otp: string,
  ): Observable<MessageResponse> {
    const endpoint = twoFactorEnabled
      ? '/api/v1/auth/2fa/disable/verify'
      : '/api/v1/auth/2fa/enable/verify';
    return this.http.post<MessageResponse>(endpoint, { challengeId, otp });
  }
}
