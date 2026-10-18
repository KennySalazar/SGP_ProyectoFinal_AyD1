package gt.usac.cunoc.sgp.archivo.service;

import gt.usac.cunoc.sgp.archivo.model.ImagenInspeccionada;
import java.nio.file.Path;

public interface InspectorImagenes {
  ImagenInspeccionada inspeccionar(Path archivoTemporal);
}
