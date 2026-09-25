import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-admin-placeholder',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <header class="page-heading">
      <p class="page-kicker">{{ 'admin.kicker' | transloco }}</p>
      <h1 class="page-title">{{ 'admin.title' | transloco }}</h1>
      <p class="page-description">{{ 'admin.description' | transloco }}</p>
    </header>
    <section class="admin-grid">
      <article>
        <i class="pi pi-users"></i>
        <div>
          <strong>{{ 'admin.usersRoles' | transloco }}</strong
          ><span>{{ 'admin.usersRolesNote' | transloco }}</span>
        </div>
        <small>{{ 'common.pending' | transloco }}</small>
      </article>
      <article>
        <i class="pi pi-file-edit"></i>
        <div>
          <strong>{{ 'admin.formVersions' | transloco }}</strong
          ><span>{{ 'admin.formVersionsNote' | transloco }}</span>
        </div>
        <small>{{ 'common.pending' | transloco }}</small>
      </article>
      <article>
        <i class="pi pi-sliders-h"></i>
        <div>
          <strong>{{ 'admin.indexWeights' | transloco }}</strong
          ><span>{{ 'admin.indexWeightsNote' | transloco }}</span>
        </div>
        <small>{{ 'common.pending' | transloco }}</small>
      </article>
      <article>
        <i class="pi pi-history"></i>
        <div>
          <strong>{{ 'admin.audit' | transloco }}</strong
          ><span>{{ 'admin.auditNote' | transloco }}</span>
        </div>
        <small>{{ 'common.pending' | transloco }}</small>
      </article>
    </section>
  `,
  styles: [
    `
      .admin-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 1rem;
      }
      .admin-grid article {
        display: grid;
        grid-template-columns: 46px minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.9rem;
        padding: 1.15rem;
        background: #fff;
        border: 1px solid #d8e2ec;
        border-radius: 16px;
        box-shadow: 0 8px 26px rgba(7, 20, 38, 0.04);
      }
      .admin-grid i {
        width: 46px;
        height: 46px;
        display: grid;
        place-items: center;
        border-radius: 12px;
        background: #eaf2ff;
        color: #2563eb;
        font-size: 1.05rem;
      }
      .admin-grid div {
        display: grid;
        gap: 0.2rem;
      }
      .admin-grid strong {
        color: #17324c;
        font-size: 0.86rem;
      }
      .admin-grid span {
        color: #71859a;
        font-size: 0.73rem;
        line-height: 1.45;
      }
      .admin-grid small {
        padding: 0.35rem 0.5rem;
        border-radius: 999px;
        background: #f1f5f9;
        color: #64748b;
        font-size: 0.65rem;
        font-weight: 800;
      }
      @media (max-width: 760px) {
        .admin-grid {
          grid-template-columns: 1fr;
        }
        .admin-grid article {
          grid-template-columns: 42px 1fr;
        }
        .admin-grid small {
          grid-column: 2;
          justify-self: start;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPlaceholderPage {}
