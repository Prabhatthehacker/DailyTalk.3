import { describe, expect, it } from 'vitest';
import { formatNumber, generateNumber, normalizeNumber } from './number';

describe('Daily Talk numbers', () => {
  it('generates valid 10-digit numbers that never start with 0', () => {
    for (let i = 0; i < 500; i++) expect(generateNumber()).toMatch(/^[1-9][0-9]{9}$/);
  });
  it('skips biased bytes and leading zeros', () => {
    // bytes: 255 (skipped), 0 (leading zero skipped), then 1..9 and 0 -> 1234567890
    const seq = [255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
    expect(generateNumber(() => Uint8Array.from(seq))).toBe('1234567890');
  });
  it('normalises typed numbers', () => {
    expect(normalizeNumber('12345 67890')).toBe('1234567890');
    expect(normalizeNumber('12345-67890')).toBe('1234567890');
  });
  it('rejects invalid numbers', () => {
    for (const bad of ['', '012345 6789', '12345', '12345678901', 'abcdefghij', '+911234567890']) expect(normalizeNumber(bad)).toBeNull();
  });
  it('formats for display and tolerates junk', () => {
    expect(formatNumber('1234567890')).toBe('12345 67890');
    expect(formatNumber('nonsense')).toBe('nonsense');
  });
});
