export interface SalidaRequest {
  dispositivoId: string;
  puentesIds: string[];
}
export interface PuenteSalida {
  id: string;
  codigo: string;
  nombre: string;
  departamento: { id: string; nombre: string };
  municipio: { id: string; nombre: string };
  latitud: number;
  longitud: number;
  activo: boolean;
}
export interface InspeccionSalida {
  id: string;
  puenteId: string;
  versionFormularioId: string;
  publicadoEn: string;
  datos: unknown;
  datosPermanentes: unknown;
}
export interface PaqueteSalida {
  formulario: { id: string; codigo: string; hashContenido: string; esquema: unknown };
  puentes: PuenteSalida[];
  inspecciones: InspeccionSalida[];
  asignaciones: { id: string; puenteId: string; cursoEstudianteId: string }[];
}
export interface PreparacionSalida {
  id: string;
  usuarioId: string;
  dispositivoId: string;
  preparadoEn: string;
  numeroPuentes: number;
  tamanoEstimadoBytes: number;
  paquete: PaqueteSalida;
}
export interface EstimacionSalida {
  versionFormularioId: string;
  codigoVersionFormulario: string;
  numeroPuentes: number;
  numeroInspeccionesPrevias: number;
  tamanoEstimadoBytes: number;
}
export interface ResumenSalida {
  propietarioId: string;
  id: string;
  dispositivoId: string;
  preparadoEn: string;
  versionFormularioId: string;
  codigoVersionFormulario: string;
  numeroPuentes: number;
  asignaciones: PaqueteSalida['asignaciones'];
}
export interface SolicitudSalidaLocal {
  propietarioId: string;
  clave: string;
  cuerpo: SalidaRequest;
}
export interface PaginaSalida {
  content: PuenteSalida[];
  totalElements: number;
  totalPages: number;
}
