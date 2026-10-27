export const user = {
  id: '0199a000-0000-7000-8000-000000000001',
  email: 'hu061@example.test',
  nombreCompleto: 'Inspector HU-061',
  nombreUsuario: 'hu061',
  role: 'CATEDRATICO',
  verified: true,
  activated: true,
  active: true,
  twoFactorEnabled: false,
};
export const schema = {
  type: 'object',
  properties: {
    identificacion: {
      type: 'object',
      title: 'Identificación',
      properties: { nombre_puente: { type: 'string', title: 'Puente' } },
    },
  },
};
export const fixture = {
  id: '0199a000-0000-7000-8000-000000000005',
  puenteId: '0199a000-0000-7000-8000-000000000003',
  versionFormularioId: '0199a000-0000-7000-8000-000000000004',
  codigoVersionFormulario: 'HU061-PRUEBA',
  autorId: user.id,
  rolAutor: 'CATEDRATICO',
  nombreAutor: user.nombreCompleto,
  nombrePuente: 'Puente de prueba HU-061',
  codigoPuente: 'HU061-01',
  dispositivoId: 'e2e-hu061',
  fechaInspeccion: '2026-10-20',
  estado: 'BORRADOR',
  version: 0,
  datos: { identificacion: { nombre_puente: 'Puente de prueba HU-061' } },
  acompanantes: [],
  creadoEn: '2026-10-20T12:00:00Z',
  progreso: {
    totalCamposRequeridos: 0,
    totalCamposCompletados: 0,
    porcentajeTotal: 100,
    secciones: [],
  },
};
export const photos = ['ACCESO', 'SUPERESTRUCTURA', 'SUBESTRUCTURA', 'CAUCE'].map((categoria) => ({
  id: categoria,
  inspeccionId: fixture.id,
  categoria,
  latitud: 14,
  longitud: -90,
  capturadoEn: '2026-10-20T12:00:00Z',
  mimeReal: 'image/jpeg',
  tamanoBytes: 100,
  anchoPx: 10,
  altoPx: 10,
}));
