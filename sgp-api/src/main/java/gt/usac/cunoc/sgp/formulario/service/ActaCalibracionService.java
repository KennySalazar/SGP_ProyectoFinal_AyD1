package gt.usac.cunoc.sgp.formulario.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.usac.cunoc.sgp.archivo.entity.Archivo;
import gt.usac.cunoc.sgp.archivo.entity.ArchivoActaCalibracion;
import gt.usac.cunoc.sgp.archivo.model.EstadoArchivo;
import gt.usac.cunoc.sgp.archivo.model.TipoUsoArchivo;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoActaCalibracionRepository;
import gt.usac.cunoc.sgp.archivo.repository.ArchivoRepository;
import gt.usac.cunoc.sgp.archivo.service.AlmacenamientoArchivos;
import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import gt.usac.cunoc.sgp.common.audit.service.AuditoriaWriter;
import gt.usac.cunoc.sgp.common.exception.ApiException;
import gt.usac.cunoc.sgp.common.util.UuidV7Generator;
import gt.usac.cunoc.sgp.formulario.dto.ActaCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.dto.EstadoActaCalibracionResponse;
import gt.usac.cunoc.sgp.formulario.entity.VersionFormulario;
import gt.usac.cunoc.sgp.formulario.repository.ConfiguracionIcElementoRepository;
import gt.usac.cunoc.sgp.formulario.repository.VersionFormularioRepository;
import gt.usac.cunoc.sgp.formulario.validation.ValidadorActaPdf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ActaCalibracionService {
    private static final Logger LOG = LoggerFactory.getLogger(ActaCalibracionService.class);
    private final VersionFormularioRepository versiones;
    private final ConfiguracionIcElementoRepository pesos;
    private final ArchivoRepository archivos;
    private final ArchivoActaCalibracionRepository actas;
    private final AlmacenamientoArchivos almacenamiento;
    private final ValidadorActaPdf validador;
    private final AuditoriaWriter auditoria;
    private final ObjectMapper json;
    private final Clock clock;

    public ActaCalibracionService(
            VersionFormularioRepository versiones,
            ConfiguracionIcElementoRepository pesos,
            ArchivoRepository archivos,
            ArchivoActaCalibracionRepository actas,
            AlmacenamientoArchivos almacenamiento,
            ValidadorActaPdf validador,
            AuditoriaWriter auditoria,
            ObjectMapper json,
            Clock clock) {
        this.versiones = versiones;
        this.pesos = pesos;
        this.archivos = archivos;
        this.actas = actas;
        this.almacenamiento = almacenamiento;
        this.validador = validador;
        this.auditoria = auditoria;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EstadoActaCalibracionResponse consultar(UUID versionId) {
        obtenerVersion(versionId);
        return estado(versionId, vigente(versionId));
    }

    @Transactional
    public EstadoActaCalibracionResponse adjuntar(
            UUID versionId, MultipartFile fichero, UUID usuarioId) {
        VersionFormulario version =
                versiones.findByIdForUpdate(versionId).orElseThrow(this::versionNoEncontrada);
        validarPesos(version);
        byte[] contenido = validador.validar(fichero);
        Archivo anterior = vigente(versionId);
        var antes = estado(versionId, anterior);
        UUID id = UuidV7Generator.generate();
        String clave = "acta-" + versionId + "-" + id + ".pdf";
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Adjuntar el acta requiere una transaccion activa.");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int estadoTransaccion) {
                        if (estadoTransaccion == STATUS_ROLLED_BACK) {
                            eliminarObjeto(clave);
                        }
                    }
                });
        try {
            almacenamiento.guardar(
                    clave, new ByteArrayInputStream(contenido), contenido.length, "application/pdf");
        } catch (IOException excepcion) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "almacenamiento_no_disponible",
                    "No fue posible almacenar el acta. Intente nuevamente.");
        }
        var ahora = clock.instant();
        Archivo nuevo =
                new Archivo(
                        id,
                        TipoUsoArchivo.ACTA_CALIBRACION,
                        null,
                        nombreSeguro(fichero.getOriginalFilename()),
                        id + ".pdf",
                        clave,
                        null,
                        "application/pdf",
                        contenido.length,
                        checksum(contenido),
                        null,
                        null,
                        null,
                        null,
                        usuarioId,
                        ahora);
        archivos.saveAndFlush(nuevo);
        actas.saveAndFlush(new ArchivoActaCalibracion(nuevo, version, ahora));
        if (anterior != null) {
            anterior.retirar(ahora);
            archivos.saveAndFlush(anterior);
        }
        version.setActualizadoEn(ahora);
        versiones.saveAndFlush(version);
        var despues = estado(versionId, nuevo);
        auditoria.registrarEnTransaccion(
                usuarioId,
                AccionAuditoria.MODIFICAR,
                "version_formulario",
                versionId,
                json.valueToTree(antes),
                json.valueToTree(despues),
                "HU-019: acta de calibracion");
        return despues;
    }

    @Transactional(readOnly = true)
    public byte[] descargar(UUID versionId) {
        obtenerVersion(versionId);
        Archivo archivo = vigente(versionId);
        if (archivo == null) {
            throw error(
                    HttpStatus.NOT_FOUND, "acta_no_encontrada", "La version esta sin acta de calibracion.");
        }
        try (var entrada = almacenamiento.abrir(archivo.getClaveObjetoOriginal())) {
            byte[] contenido = entrada.readNBytes(ValidadorActaPdf.MAXIMO_BYTES + 1);
            if (contenido.length != archivo.getTamanoBytes()
                    || !checksum(contenido).equals(archivo.getChecksumSha256())) {
                throw error(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "acta_no_disponible",
                        "El acta no esta disponible. Contacte al administrador.");
            }
            return contenido;
        } catch (IOException excepcion) {
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "almacenamiento_no_disponible",
                    "No fue posible consultar el acta.");
        }
    }

    private VersionFormulario obtenerVersion(UUID id) {
        return versiones.findById(id).orElseThrow(this::versionNoEncontrada);
    }

    private Archivo vigente(UUID id) {
        return actas
                .findFirstByVersionFormularioIdAndArchivoEstadoOrderByCreadoEnDescArchivoIdDesc(
                        id, EstadoArchivo.DISPONIBLE)
                .map(ArchivoActaCalibracion::getArchivo)
                .orElse(null);
    }

    private void validarPesos(VersionFormulario version) {
        var configurados = new HashSet<String>();
        for (var peso : pesos.findAllByVersionFormularioId(version.getId())) {
            if (peso.getPeso() != null && peso.getPeso().signum() > 0) {
                configurados.add(peso.getElementoRef());
            }
        }
        var catalogo = version.getReglasEvaluacionIc();
        if (catalogo == null
                || !catalogo.path("elementos").isArray()
                || catalogo.path("elementos").isEmpty()) {
            throw error(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "pesos_incompletos",
                    "Configure el catalogo y los pesos antes de adjuntar el acta.");
        }
        for (var elemento : catalogo.path("elementos")) {
            if (!configurados.contains(elemento.path("elemento_ref").asText())) {
                throw error(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "pesos_incompletos",
                        "Falta configurar el peso de " + elemento.path("elemento_ref").asText());
            }
        }
    }

    private EstadoActaCalibracionResponse estado(UUID versionId, Archivo archivo) {
        ActaCalibracionResponse acta =
                archivo == null
                        ? null
                        : new ActaCalibracionResponse(
                        archivo.getId(),
                        archivo.getNombreOriginal(),
                        archivo.getMimeReal(),
                        archivo.getTamanoBytes(),
                        archivo.getChecksumSha256(),
                        archivo.getSubidoPorId(),
                        archivo.getConfirmadoEn());
        return new EstadoActaCalibracionResponse(
                versionId,
                archivo == null ? "SIN_ACTA" : "CON_ACTA",
                archivo == null ? "Sin acta de calibracion" : "Acta de calibracion disponible",
                acta);
    }

    private void eliminarObjeto(String clave) {
        try {
            almacenamiento.eliminar(clave);
        } catch (IOException | RuntimeException excepcion) {
            LOG.error("No fue posible limpiar el objeto de acta tras rollback: {}", clave, excepcion);
        }
    }

    private String nombreSeguro(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "acta-calibracion.pdf";
        }
        String seguro = nombre.replace('\\', '/');
        seguro = seguro.substring(seguro.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return seguro.isBlank()
                ? "acta-calibracion.pdf"
                : seguro.substring(0, Math.min(seguro.length(), 255));
    }

    private String checksum(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException excepcion) {
            throw new IllegalStateException("SHA-256 no disponible", excepcion);
        }
    }

    private ApiException versionNoEncontrada() {
        return error(
                HttpStatus.NOT_FOUND,
                "version_no_encontrada",
                "No existe la version de formulario solicitada.");
    }

    private ApiException error(HttpStatus estado, String codigo, String detalle) {
        return new ApiException(estado, codigo, "Acta de calibracion", detalle);
    }
}