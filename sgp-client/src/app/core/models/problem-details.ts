export interface FieldError { campo?: string; mensaje?: string; }
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errores?: FieldError[];
}
