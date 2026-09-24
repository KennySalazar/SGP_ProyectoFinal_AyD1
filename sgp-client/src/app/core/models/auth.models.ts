export type RoleName = 'ADMINISTRADOR' | 'CATEDRATICO' | 'PROFESIONAL_EXTERNO' | 'ESTUDIANTE';

export interface UserSession {
  id: string;
  email: string;
  role: RoleName;
  verified: boolean;
  activated: boolean;
  active: boolean;
  twoFactorEnabled: boolean;
}

export interface LoginResponse {
  requiresTwoFactor: boolean;
  challengeId: string | null;
  accessToken: string | null;
  tokenType: string | null;
  expiresInMs: number | null;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
}

export interface ChallengeResponse {
  challengeId: string;
  expiresAt: string;
  message: string;
}

export interface MessageResponse { message: string; }
