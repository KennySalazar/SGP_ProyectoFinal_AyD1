import { test, expect } from '@playwright/test';
import { user } from './fixture.mjs';

const base = '/api/v1/ordenes-mantenimiento';
// Solo API simulada; no utiliza datos ni servicios del despliegue.
async function preparar(page, estado = 'EN_EJECUCION') {
  await page.clock.setFixedTime(new Date('2026-10-28T18:00:00-06:00'));
  const orden = {
    id: 'calendario-prueba',
    puenteId: 'puente-prueba',
    estado,
    tipo: 'CORRECTIVO',
    descripcion: 'Reparar barandal',
    prioridadConfirmada: 'ALTA',
    prioridadSugerida: 'ALTA',
    version: 2,
    creadoEn: '2026-10-20T15:00:00Z',
  };
  let ejecucion = {
    ordenId: orden.id,
    iniciadoEn: '2026-10-20T15:00:00Z',
    fechaEjecucion: null,
    responsableUsuarioId: user.id,
    responsableUsuarioEmail: user.email,
    responsableNombre: null,
  };
  const escrituras = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    let body = [];
    if (url.pathname.endsWith('/auth/refresh')) body = { accessToken: 'prueba-local' };
    else if (url.pathname.endsWith('/auth/me')) body = user;
    else if (url.pathname === `${base}/${orden.id}`) body = orden;
    else if (url.pathname.endsWith('/ejecucion')) {
      if (request.method() === 'PUT') {
        escrituras.push(request.postDataJSON());
        ejecucion = { ...ejecucion, ...request.postDataJSON() };
      }
      body = ejecucion;
    } else if (url.pathname.endsWith('/programar')) {
      escrituras.push(request.postDataJSON());
      Object.assign(orden, request.postDataJSON(), { estado: 'PROGRAMADA' });
      body = orden;
    } else if (url.pathname.endsWith('/responsables')) body = [user];
    else if (url.pathname === '/api/v1/puentes/puente-prueba')
      body = { id: 'puente-prueba', codigo: 'GT-01', nombre: 'Puente de prueba' };
    else if (url.pathname === '/api/v1/puentes' || url.pathname === base)
      body = { content: [], totalElements: 0, size: 20, number: 0 };
    else if (url.pathname.endsWith('/totales')) body = { total: 0, porEstado: {} };
    await route.fulfill({ json: body });
  });
  await page.goto('/mantenimiento/ordenes/calendario-prueba');
  await expect(page.getByRole('heading', { name: orden.descripcion })).toBeVisible();
  return escrituras;
}

async function comprobarPanel(page, input) {
  await expect(input).toHaveAttribute('aria-expanded', 'true');
  await expect(input).toHaveAttribute('aria-controls', /.+/);
  const panel = page.locator(`[id="${await input.getAttribute('aria-controls')}"]`);
  await expect(panel).toBeVisible();
  await expect(panel.locator('..')).not.toHaveClass(/(?:enter|leave)-active/);
  await expect(panel.locator('..')).toHaveCSS('opacity', '1');
  const medidas = await panel.evaluate((el) => {
    const css = getComputedStyle(el);
    const r = el.getBoundingClientRect();
    return {
      fondo: css.backgroundColor,
      sombra: css.boxShadow,
      token: css.getPropertyValue('--p-datepicker-panel-background').trim(),
      izquierda: r.left,
      derecha: r.right,
      ancho: innerWidth,
      encima: el.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)),
    };
  });
  expect(medidas.fondo).toBe('rgb(255, 255, 255)');
  expect(medidas.token).not.toBe('');
  expect(medidas.sombra).not.toBe('none');
  expect(medidas.izquierda).toBeGreaterThanOrEqual(0);
  expect(medidas.derecha).toBeLessThanOrEqual(medidas.ancho);
  expect(medidas.encima).toBe(true);
  await expect(panel.locator('thead th')).toHaveCount(7);
  await page.screenshot({
    path: test.info().outputPath(`calendario-${test.info().title.length}.png`),
    fullPage: false,
  });
  return panel;
}

for (const viewport of [
  { width: 1280, height: 900 },
  { width: 360, height: 800 },
]) {
  test.describe(`Calendario mantenimiento ${viewport.width}px`, () => {
    test.use({ viewport, timezoneId: 'America/Guatemala', serviceWorkers: 'block' });
    test('ejecución: límites, selección, guardado, recarga y teclado', async ({ page }) => {
      const escrituras = await preparar(page);
      const input = page.locator('#detalle-1');
      await input.locator('..').getByRole('button').click();
      let panel = await comprobarPanel(page, input);
      await expect(panel.locator('[data-date="2026-9-19"]')).toHaveClass(/p-disabled/);
      await expect(panel.locator('[data-date="2026-9-29"]')).toHaveClass(/p-disabled/);
      await panel.locator('[data-date="2026-9-25"]').click();
      await expect(input).toHaveValue('25/10/2026');
      await expect(panel).toBeHidden();
      await page.getByRole('radio', { name: 'Persona o empresa externa' }).check();
      await page
        .getByRole('textbox', { name: 'Persona o empresa externa' })
        .fill('Constructora de prueba');
      await page.getByRole('button', { name: 'Guardar ejecución' }).click();
      await expect(page.getByRole('status')).toContainText('guardada');
      expect(escrituras).toEqual([
        {
          fechaEjecucion: '2026-10-25',
          responsableUsuarioId: null,
          responsableNombre: 'Constructora de prueba',
        },
      ]);
      await page.reload();
      await expect(input).toHaveValue('25/10/2026');
      await expect(page.getByRole('textbox', { name: 'Persona o empresa externa' })).toHaveValue(
        'Constructora de prueba',
      );
      await input.focus();
      panel = await comprobarPanel(page, input);
      await input.press('ArrowDown');
      const dia = panel.locator('[data-date="2026-9-28"]');
      await expect(dia).toBeFocused();
      expect(await dia.evaluate((el) => getComputedStyle(el).outlineStyle)).not.toBe('none');
      await dia.press('ArrowLeft');
      await page.keyboard.press('Enter');
      await expect(input).toHaveValue('27/10/2026');
      await input.press('ArrowDown');
      await page.keyboard.press('Escape');
      await expect(panel).toBeHidden();
      await expect(input).toBeFocused();
      await input.click();
      // Punto fuera del panel y de los controles en ambos tamaños de pantalla.
      await page.mouse.click(5, 150);
      await expect(panel).toBeHidden();
    });
    test('programación: bloquea días pasados y programa la fecha elegida', async ({ page }) => {
      const escrituras = await preparar(page, 'PROPUESTA');
      await page.getByRole('button', { name: 'Programar', exact: true }).click();
      const dialogo = page.getByRole('dialog', { name: 'Programar la orden', exact: true });
      await expect(dialogo).toBeVisible();
      await expect(dialogo).not.toHaveClass(/(?:enter|leave)-active/);
      const input = page.locator('#detalle-2');
      // El autofocus del diálogo ya abre el calendario: el icono lo alternaría y lo cerraría.
      await expect(input).toBeFocused();
      await input.click();
      const panel = await comprobarPanel(page, input);
      await expect(panel.locator('[data-date="2026-9-27"]')).toHaveClass(/p-disabled/);
      await panel.locator('[data-date="2026-9-30"]').click();
      await expect(input).toHaveValue('30/10/2026');
      await expect(panel).toBeHidden();
      await page
        .getByRole('dialog')
        .getByRole('button', { name: 'Programar', exact: true })
        .click();
      await expect(
        page.locator('.acciones-encabezado').getByRole('button', { name: 'Iniciar ejecución' }),
      ).toBeVisible();
      expect(escrituras).toEqual([{ fechaProgramada: '2026-10-30', version: 2 }]);
    });
    test('filtros compartidos: desde/hasta mantienen selección y límites', async ({ page }) => {
      await preparar(page);
      await page.goto('/mantenimiento/ordenes');
      const desde = page.locator('#filtro-4');
      await desde.click();
      let panel = await comprobarPanel(page, desde);
      await panel.locator('[data-date="2026-9-25"]').click();
      await expect(desde).toHaveValue('25/10/2026');
      await expect(panel).toBeHidden();
      const hasta = page.locator('#filtro-5');
      await hasta.click();
      panel = await comprobarPanel(page, hasta);
      await expect(panel.locator('[data-date="2026-9-24"]')).toHaveClass(/p-disabled/);
      await panel.locator('[data-date="2026-9-28"]').click();
      await expect(hasta).toHaveValue('28/10/2026');
    });
  });
}
