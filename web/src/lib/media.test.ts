import { describe, expect, it } from 'vitest';
import { HOSTED_URL, mediaKind, parseUploadResponse, safeUploadName, validateMedia } from './media';

describe('media validation', () => {
  it('accepts supported photos and videos up to the limit', () => {
    expect(validateMedia({ type: 'image/png', size: 1000 })).toBeNull();
    expect(validateMedia({ type: 'video/mp4', size: 200_000_000 })).toBeNull();
  });
  it('rejects empty, oversized and unsupported files', () => {
    expect(validateMedia({ type: 'video/mp4', size: 0 })).toMatch(/empty/);
    expect(validateMedia({ type: 'video/mp4', size: 200_000_001 })).toMatch(/larger than 200 MB/);
    expect(validateMedia({ type: 'application/pdf', size: 10 })).toMatch(/Only photos/);
  });
  it('classifies kinds', () => {
    expect(mediaKind('image/webp')).toBe('image');
    expect(mediaKind('video/quicktime')).toBe('video');
    expect(mediaKind('text/plain')).toBeNull();
  });
  it('only trusts files.catbox.moe links from the host', () => {
    expect(parseUploadResponse('https://files.catbox.moe/abc123.png\n')).toBe('https://files.catbox.moe/abc123.png');
    expect(() => parseUploadResponse('https://evil.example/x.png')).toThrow();
    expect(() => parseUploadResponse('No files given.')).toThrow(/refused/);
    expect(HOSTED_URL.test('https://files.catbox.moe/../x.png')).toBe(false);
  });
  it('keeps a file extension on uploaded names', () => {
    expect(safeUploadName('my photo?.jpg')).toBe('my_photo_.jpg');
    expect(safeUploadName('noext')).toBe('noext.bin');
  });
});
