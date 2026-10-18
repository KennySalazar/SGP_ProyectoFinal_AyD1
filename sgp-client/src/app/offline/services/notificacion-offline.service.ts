import { Injectable } from '@angular/core';
import { LocalNotificacion, sgpDb } from '../db/sgp-db';

@Injectable({ providedIn: 'root' })
export class NotificacionOfflineService {
  private readonly db = sgpDb;

  async guardarAvisos(avisos: LocalNotificacion[], conteoNoLeidas?: number): Promise<void> {
    await this.db.transaction(
      'rw',
      this.db.notificaciones,
      this.db.notificaciones_meta,
      async () => {
        await this.db.notificaciones.bulkPut(avisos);
        if (conteoNoLeidas !== undefined) {
          await this.db.notificaciones_meta.put({
            key: 'conteo_no_leidas',
            valor: conteoNoLeidas,
          });
        }
      },
    );
  }

  async guardarConteo(conteoNoLeidas: number): Promise<void> {
    await this.db.notificaciones_meta.put({
      key: 'conteo_no_leidas',
      valor: conteoNoLeidas,
    });
  }

  async obtenerConteo(): Promise<number> {
    const registro = await this.db.notificaciones_meta.get('conteo_no_leidas');
    if (registro && typeof registro.valor === 'number') {
      return registro.valor;
    }
    // Si no está registrado en meta, calculamos cuántas no leídas hay en Dexie
    const noLeidas = await this.db.notificaciones.filter((n) => !n.leidoEn).count();
    return noLeidas;
  }

  async obtenerAvisos(soloNoLeidos = false): Promise<LocalNotificacion[]> {
    let items: LocalNotificacion[];
    if (soloNoLeidos) {
      items = await this.db.notificaciones.filter((n) => !n.leidoEn).toArray();
    } else {
      items = await this.db.notificaciones.toArray();
    }

    return items.sort((a, b) => (b.creadoEn || '').localeCompare(a.creadoEn || ''));
  }

  async marcarLeidaLocal(id: string): Promise<void> {
    const aviso = await this.db.notificaciones.get(id);
    if (aviso && !aviso.leidoEn) {
      aviso.leidoEn = new Date().toISOString();
      await this.db.notificaciones.put(aviso);
      const conteoActual = await this.obtenerConteo();
      await this.guardarConteo(Math.max(0, conteoActual - 1));
    }
  }

  async marcarTodasLeidasLocal(): Promise<void> {
    const ahora = new Date().toISOString();
    await this.db.transaction(
      'rw',
      this.db.notificaciones,
      this.db.notificaciones_meta,
      async () => {
        const sinLeer = await this.db.notificaciones.filter((n) => !n.leidoEn).toArray();
        for (const n of sinLeer) {
          n.leidoEn = ahora;
          await this.db.notificaciones.put(n);
        }
        await this.db.notificaciones_meta.put({
          key: 'conteo_no_leidas',
          valor: 0,
        });
      },
    );
  }
}
