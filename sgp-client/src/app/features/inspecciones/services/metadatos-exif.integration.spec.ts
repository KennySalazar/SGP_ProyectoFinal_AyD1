import { describe, expect, it } from 'vitest';
import { parse, gps } from 'exifr';
import { fechaExif } from './procesador-fotografias.service';
/** JPEG de prueba generado localmente con EXIF GPS y zona horaria explícita. */
const jpeg = Uint8Array.from(
  atob(
    '/9j/4AAQSkZJRgABAQAAAQABAAD/4QDaRXhpZgAATU0AKgAAAAgAAwESAAMAAAABAAYAAIdpAAQAAAABAAAAMoglAAQAAAABAAAAbAAAAAAAApADAAIAAAAUAAAAUJARAAIAAAAHAAAAZAAAAAAyMDI2OjEwOjE0IDEwOjIwOjMwAC0wNjowMAAAAAQAAQACAAAAAk4AAAAAAgAFAAAAAwAAAKIAAwACAAAAAlcAAAAABAAFAAAAAwAAALoAAAAAAAAADgAAAAEAAAAAAAAAAQAAAAAAAAABAAAAWgAAAAEAAAAAAAAAAQAAAAAAAAAB/9sAQwAIBgYHBgUIBwcHCQkICgwUDQwLCwwZEhMPFB0aHx4dGhwcICQuJyAiLCMcHCg3KSwwMTQ0NB8nOT04MjwuMzQy/9sAQwEJCQkMCwwYDQ0YMiEcITIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy/8AAEQgAFAAoAwEiAAIRAQMRAf/EAB8AAAEFAQEBAQEBAAAAAAAAAAABAgMEBQYHCAkKC//EALUQAAIBAwMCBAMFBQQEAAABfQECAwAEEQUSITFBBhNRYQcicRQygZGhCCNCscEVUtHwJDNicoIJChYXGBkaJSYnKCkqNDU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6g4SFhoeIiYqSk5SVlpeYmZqio6Slpqeoqaqys7S1tre4ubrCw8TFxsfIycrS09TV1tfY2drh4uPk5ebn6Onq8fLz9PX29/j5+v/EAB8BAAMBAQEBAQEBAQEAAAAAAAABAgMEBQYHCAkKC//EALURAAIBAgQEAwQHBQQEAAECdwABAgMRBAUhMQYSQVEHYXETIjKBCBRCkaGxwQkjM1LwFWJy0QoWJDThJfEXGBkaJicoKSo1Njc4OTpDREVGR0hJSlNUVVZXWFlaY2RlZmdoaWpzdHV2d3h5eoKDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uLj5OXm5+jp6vLz9PX29/j5+v/aAAwDAQACEQMRAD8A8cooor9xPMCiiigAooooAKKKKACiiigAooooA//Z',
  ),
  (c) => c.charCodeAt(0),
);
describe('EXIF real antes de comprimir', () => {
  it('lee fecha, offset, GPS y orientación desde bytes JPEG', async () => {
    const tags = await parse(jpeg, {
      pick: ['DateTimeOriginal', 'OffsetTimeOriginal', 'Orientation'],
      reviveValues: false,
      translateValues: false,
    });
    expect(tags.Orientation).toBe(6);
    expect(fechaExif(tags.DateTimeOriginal, tags.OffsetTimeOriginal)).toBe(
      '2026-10-14T16:20:30.000Z',
    );
    expect(await gps(jpeg)).toEqual({ latitude: 14, longitude: -90 });
  });
});
