package gt.usac.cunoc.sgp.sync.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

public class SolicitudIdempotente extends HttpServletRequestWrapper {
  private final byte[] cuerpo;

  public SolicitudIdempotente(HttpServletRequest request, byte[] cuerpo) {
    super(request);
    this.cuerpo = cuerpo;
  }

  @Override
  public String getHeader(String nombre) {
    return "Idempotency-Key".equalsIgnoreCase(nombre) ? null : super.getHeader(nombre);
  }

  @Override
  public Enumeration<String> getHeaders(String nombre) {
    return "Idempotency-Key".equalsIgnoreCase(nombre)
        ? Collections.emptyEnumeration()
        : super.getHeaders(nombre);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    return Collections.enumeration(
        Collections.list(super.getHeaderNames()).stream()
            .filter(n -> !"Idempotency-Key".equalsIgnoreCase(n))
            .toList());
  }

  @Override
  public ServletInputStream getInputStream() {
    var entrada = new ByteArrayInputStream(cuerpo);
    return new ServletInputStream() {
      @Override
      public int read() {
        return entrada.read();
      }

      @Override
      public boolean isFinished() {
        return entrada.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {
        throw new UnsupportedOperationException("Solo se admiten operaciones sincronas");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
