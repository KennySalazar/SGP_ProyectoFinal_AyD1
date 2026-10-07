package gt.usac.cunoc.sgp.common.audit.aspect;

import gt.usac.cunoc.sgp.common.audit.model.AccionAuditoria;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

  AccionAuditoria accion();

  // nombre de entidad afectada
  String entidad();

  String proceso() default "";

  String idArg() default "";

  Class<?> tipo() default Void.class;

  int emailArgIndex() default -1;

  /** En auto-registro, el usuario creado es quien inició la acción anónima. */
  boolean actorEsEntidad() default false;
}
