import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-auth-card',
  standalone: true,
  template: `
    <main class="auth-page">
      <section class="auth-visual" aria-hidden="true">
        <div class="visual-grid"></div>
        <div class="visual-content">
          <div class="visual-brand"><span>SGP</span><strong>Sistema de Gestion de Puentes</strong></div>
          <div class="visual-copy">
            <p>PLATAFORMA TECNICA</p>
            <h2>Informacion confiable para decisiones de infraestructura.</h2>
            <div class="visual-points">
              <span><i class="pi pi-map-marker"></i> Inventario georreferenciado</span>
              <span><i class="pi pi-file-check"></i> Inspeccion y trazabilidad</span>
              <span><i class="pi pi-cloud-download"></i> Trabajo de campo offline</span>
            </div>
          </div>
          <small>CUNOC · Analisis y Diseno de Sistemas 1</small>
        </div>
      </section>

      <section class="auth-workspace">
        <div class="auth-card" aria-labelledby="auth-title">
          <div class="mobile-brand"><span>SGP</span><strong>Gestion de Puentes</strong></div>
          <p class="kicker">ACCESO SEGURO</p>
          <h1 id="auth-title">{{ title }}</h1>
          <p class="subtitle">{{ subtitle }}</p>
          <ng-content />
          <p class="security-note"><i class="pi pi-lock"></i> Conexion protegida y sesion controlada</p>
        </div>
      </section>
    </main>
  `,
  styles: [`
    .auth-page{min-height:100vh;display:grid;grid-template-columns:minmax(340px,.9fr) minmax(420px,1.1fr);background:#eef3f8}
    .auth-visual{position:relative;overflow:hidden;min-height:100vh;background:linear-gradient(155deg,#071426 0%,#0b1f33 55%,#123a5c 100%);color:#fff}
    .auth-visual::after{content:'';position:absolute;width:520px;height:520px;border:1px solid rgba(96,165,250,.2);border-radius:50%;right:-180px;bottom:-180px;box-shadow:0 0 0 70px rgba(96,165,250,.035),0 0 0 140px rgba(96,165,250,.025)}
    .visual-grid{position:absolute;inset:0;background-image:linear-gradient(rgba(147,197,253,.055) 1px,transparent 1px),linear-gradient(90deg,rgba(147,197,253,.055) 1px,transparent 1px);background-size:36px 36px;mask-image:linear-gradient(to bottom,black,transparent 90%)}
    .visual-content{position:relative;z-index:1;height:100%;min-height:100vh;display:flex;flex-direction:column;padding:clamp(2rem,5vw,4.5rem)}
    .visual-brand{display:flex;align-items:center;gap:.8rem}.visual-brand span,.mobile-brand span{width:46px;height:46px;display:grid;place-items:center;border-radius:12px;background:#fff;color:#0b1f33;font-weight:900}.visual-brand strong{font-size:.9rem;color:#dce8f4}
    .visual-copy{margin:auto 0;max-width:34rem}.visual-copy>p,.kicker{margin:0 0 .7rem;color:#60a5fa;font-size:.72rem;font-weight:900;letter-spacing:.16em}.visual-copy h2{margin:0;font-size:clamp(2.1rem,4vw,3.7rem);line-height:1.02;letter-spacing:-.05em}.visual-points{display:grid;gap:.85rem;margin-top:2rem;color:#b9cde0}.visual-points span{display:flex;align-items:center;gap:.7rem}.visual-points i{color:#60a5fa}.visual-content>small{color:#67839d;letter-spacing:.05em}
    .auth-workspace{display:grid;place-items:center;padding:2rem;background:radial-gradient(circle at 80% 10%,rgba(37,99,235,.08),transparent 27%)}
    .auth-card{width:min(100%,31rem);padding:clamp(1.7rem,4vw,2.6rem);background:#fff;border:1px solid #d8e2ec;border-radius:20px;box-shadow:0 24px 70px rgba(7,20,38,.09)}
    .mobile-brand{display:none;align-items:center;gap:.7rem;margin-bottom:1.6rem;color:#0b1f33}.mobile-brand span{background:#0b1f33;color:#fff}.mobile-brand strong{font-size:.9rem}
    h1{margin:0;color:#0b1f33;font-size:2rem;letter-spacing:-.04em}.subtitle{margin:.55rem 0 1.6rem;color:#63778d;line-height:1.6}
    .security-note{display:flex;align-items:center;justify-content:center;gap:.45rem;margin:1.6rem 0 0;padding-top:1rem;border-top:1px solid #e5edf4;color:#8798aa;font-size:.72rem}.security-note i{font-size:.72rem}
    @media(max-width:900px){.auth-page{grid-template-columns:1fr}.auth-visual{display:none}.auth-workspace{min-height:100vh;padding:1rem}.mobile-brand{display:flex}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AuthCardComponent {
  @Input({ required: true }) title = '';
  @Input({ required: true }) subtitle = '';
}
