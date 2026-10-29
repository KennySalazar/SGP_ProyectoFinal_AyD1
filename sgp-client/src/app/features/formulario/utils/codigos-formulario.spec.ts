import { describe, expect, it } from 'vitest';
import { claveDesdeTitulo, siguienteCodigoVersion } from './codigos-formulario';

describe('claveDesdeTitulo', () => {
  it('quita la numeración, los acentos y los símbolos', () => {
    expect(claveDesdeTitulo('9. Cimentación y pilotes')).toBe('cimentacion_y_pilotes');
    expect(claveDesdeTitulo('Ancho de acera derecha (m)')).toBe('ancho_de_acera_derecha_m');
  });

  it('devuelve vacío cuando no hay caracteres utilizables', () => {
    expect(claveDesdeTitulo('  ¿¡? ')).toBe('');
  });

  it('produce siempre una clave válida para el formulario', () => {
    expect(claveDesdeTitulo('Ñandú: señal / ÁÉÍ 2')).toMatch(/^[a-z0-9_]+$/);
  });

  it('limita la longitud', () => {
    expect(claveDesdeTitulo('a'.repeat(200))).toHaveLength(60);
  });
});

describe('siguienteCodigoVersion', () => {
  it('propone el consecutivo del mayor número existente', () => {
    expect(siguienteCodigoVersion(['SIECA-v5', 'SIECA-v4', 'SIECA-v1'])).toBe('SIECA-v6');
    expect(siguienteCodigoVersion(['SIECA-v1', 'SIECA-v10', 'SIECA-v2'])).toBe('SIECA-v11');
  });

  it('usa el prefijo más frecuente e ignora códigos sin número', () => {
    expect(siguienteCodigoVersion(['PRUEBA-a', 'SIECA-v2', 'SIECA-v3', 'X-9'])).toBe('SIECA-v4');
  });

  it('sin versiones previas empieza en SIECA-v1', () => {
    expect(siguienteCodigoVersion([])).toBe('SIECA-v1');
    expect(siguienteCodigoVersion(['sin-numero'])).toBe('SIECA-v1');
  });
});
