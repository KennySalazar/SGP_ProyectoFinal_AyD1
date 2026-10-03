package gt.usac.cunoc.sgp.common.config;

import gt.usac.cunoc.sgp.usuario.service.AdminProvisioningService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap {

  private final BootstrapProperties properties;
  private final AdminProvisioningService adminProvisioningService;

  public AdminBootstrap(
      BootstrapProperties properties, AdminProvisioningService adminProvisioningService) {
    this.properties = properties;
    this.adminProvisioningService = adminProvisioningService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void provisionAdmin() {
    adminProvisioningService.provisionAdmin(properties.getEmail(), properties.getInitialPassword());
  }
}
