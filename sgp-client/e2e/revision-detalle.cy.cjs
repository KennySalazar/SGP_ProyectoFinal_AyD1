/* Pruebas de presentación contra ng serve; HTTP interceptado únicamente en el navegador de pruebas. */
const uuid = '019abc00-0000-7000-8000-000000000001';
const detalle = {
  revisionId: 'r',
  inspeccionId: 'i',
  estadoRevision: 'EN_CURSO',
  revisorId: 'revisor',
  numeroCiclo: 2,
  enviadaEn: '2026-10-18T12:00:00Z',
  autorId: 'autor',
  puenteId: 'puente',
  fechaInspeccion: '2026-10-17',
  versionFormularioId: 'v1',
  versionFormularioCodigo: 'Histórica-v1',
  esquemaFormulario: {
    type: 'object',
    properties: {
      identificacion: {
        type: 'object',
        title: 'Identificación histórica',
        properties: { nombre: { type: 'string', title: 'Nombre histórico' } },
      },
      tramos: {
        type: 'array',
        title: 'Tramos',
        items: {
          type: 'object',
          properties: {
            instancia_id: { type: 'string', format: 'uuid', readOnly: true },
            longitud: { type: 'number', title: 'Longitud' },
          },
        },
      },
    },
  },
  datos: { identificacion: { nombre: 'Enviado' }, tramos: [{ instancia_id: uuid, longitud: 10 }] },
  identificacionCapturada: { nombre_puente: 'Capturado' },
  icPreliminar: { indice: 12.5, estado: 'BUENO', etiqueta: 'PRELIMINAR' },
  fotografiasEndpoint: '/api/v1/inspecciones/i/fotografias',
  fotografiasCongeladas: false,
  observacionesEndpoint: '/api/v1/revisiones/r/observaciones',
};
const observacion = {
  id: 'o',
  revisionId: 'r',
  destino: {
    tipo: 'ELEMENTO',
    elementoRef: '/tramos/longitud',
    instanciaElementoId: uuid,
    categoriaFotografica: null,
  },
  texto: 'Verificar longitud.',
  estado: 'PENDIENTE',
  creadoPorId: 'revisor',
  creadoEn: '2026-10-19T12:00:00Z',
};
function abrir(ancho) {
  cy.viewport(ancho, 812);
  cy.intercept('POST', '/api/v1/auth/refresh', {
    accessToken: 'prueba',
    tokenType: 'Bearer',
    expiresInMs: 3600000,
  });
  cy.intercept('GET', '/api/v1/auth/me', {
    id: 'revisor',
    email: 'revisor@example.com',
    role: 'CATEDRATICO',
    verified: true,
    activated: true,
    active: true,
    twoFactorEnabled: false,
  });
  cy.intercept('GET', '/api/v1/revisiones/pendientes/contador', { pendientes: 1 });
  cy.intercept('GET', '/api/v1/revisiones/r', detalle).as('detalle');
  cy.intercept('GET', '/api/v1/revisiones/r/observaciones', {
    revisionId: 'r',
    observaciones: [observacion],
    total: 1,
    pendientes: 1,
    general: { revisionId: 'r', texto: null },
  });
  cy.intercept('GET', '/api/v1/inspecciones/i/fotografias', []);
  cy.visit('/revisiones/r');
  cy.wait('@detalle', { requestTimeout: 20000 });
  cy.contains('IC PRELIMINAR: 12.5').should('be.visible');
}
const panel = () => cy.contains('app-detalle-revision-page .resumen button', 'Observaciones');
function verificarControles(selector) {
  cy.get(selector)
    .filter(':visible')
    .each(($el) => {
      const rect = $el[0].getBoundingClientRect();
      expect(rect.width).to.be.at.least(44);
      expect(rect.height).to.be.at.least(44);
    });
}
describe('HU-037: detalle, foco y móvil', () => {
  it('a 375 px no desborda, conserva controles y usa la hoja inferior existente', () => {
    abrir(375);
    cy.document().then((doc) => expect(doc.documentElement.scrollWidth).to.be.at.most(375));
    verificarControles(
      'app-detalle-revision-page button:not([disabled]),app-detalle-revision-page a[pbutton]',
    );
    panel().focus().click();
    cy.get('[role="dialog"]').should('be.visible').and('have.attr', 'aria-modal', 'true');
    cy.get('[role="dialog"]').then(($el) => {
      const r = $el[0].getBoundingClientRect();
      expect(r.width).to.be.at.most(375);
      expect(r.bottom).to.be.at.most(813);
    });
    cy.contains('[role="dialog"] button', 'Cerrar observaciones').should('be.focused');
    verificarControles('[role="dialog"] button');
    cy.focused().trigger('keydown', { key: 'Escape', code: 'Escape' });
    cy.get('[role="dialog"]').should('not.exist');
    panel().should('be.focused');
  });
  it('permite Tab, conserva el foco modal y navega a la instancia exacta al cerrar', () => {
    abrir(375);
    panel().focus().click();
    cy.contains('[role="dialog"] button', 'Cerrar observaciones').should('be.focused');
    cy.then(() =>
      Cypress.automation('remote:debugger:protocol', {
        command: 'Input.dispatchKeyEvent',
        params: {
          type: 'keyDown',
          key: 'Tab',
          code: 'Tab',
          windowsVirtualKeyCode: 9,
          modifiers: 8,
        },
      }),
    );
    cy.focused().should(($el) => expect($el.closest('[role="dialog"]').length).to.equal(1));
    cy.contains('[role="dialog"] button', 'Ir al elemento').click();
    cy.get('[role="dialog"]').should('not.exist');
    cy.get(`[data-elemento-ref="/tramos/longitud"][data-instancia-id="${uuid}"]`)
      .should('be.focused')
      .and('be.visible');
    cy.get('app-formulario-dinamico input').each(($el) => expect($el).to.be.disabled);
  });
  it('en escritorio el panel ocupa una columna sin cubrir el formulario', () => {
    abrir(1440);
    panel().click();
    cy.get('app-panel-observaciones aside').should('be.visible');
    cy.get('[role="dialog"]').should('not.exist');
    cy.get('app-panel-observaciones').then(($panel) =>
      cy
        .get('app-detalle-revision-page .contenido')
        .then(($main) =>
          expect($main[0].getBoundingClientRect().right).to.be.at.most(
            $panel[0].getBoundingClientRect().left,
          ),
        ),
    );
    cy.contains('aside button', 'Cerrar observaciones').should('be.focused');
  });
});
