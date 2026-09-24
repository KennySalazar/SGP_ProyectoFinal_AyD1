
package gt.usac.cunoc.sgp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.refresh-cookie")
public class RefreshCookieProperties {

    private String name = "SGP_REFRESH_TOKEN";
    private boolean secure;
    private String sameSite = "Strict";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public String getSameSite() { return sameSite; }
    public void setSameSite(String sameSite) { this.sameSite = sameSite; }
}
