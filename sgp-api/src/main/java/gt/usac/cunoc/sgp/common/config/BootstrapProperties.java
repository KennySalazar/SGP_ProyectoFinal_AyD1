
package gt.usac.cunoc.sgp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap.admin")
public class BootstrapProperties {

    private String email;
    private String initialPassword;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
}
