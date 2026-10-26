/** Daily Talk numbers: 10 digits, first digit 1-9, assigned by the app at sign-up and never changed.
 *  They are NOT telephone numbers and cannot be used to call or text outside this app. */

const PATTERN = /^[1-9][0-9]{9}$/;

/** Random number using the browser's cryptographic generator (rejection sampling avoids bias). */
export function generateNumber(random: (n: number) => Uint8Array = (n) => crypto.getRandomValues(new Uint8Array(n))): string {
  let digits = '';
  while (digits.length < 10) {
    for (const b of random(16)) {
      if (b >= 250) continue; // 250 is a multiple of 10, so b % 10 is unbiased
      const d = b % 10;
      if (digits.length === 0 && d === 0) continue;
      digits += d;
      if (digits.length === 10) break;
    }
  }
  return digits;
}

/** Accepts "12345 67890", "12345-67890" etc. Returns the 10 digits, or null if it is not a valid number. */
export function normalizeNumber(raw: string): string | null {
  const digits = raw.replace(/[\s\-.()]/g, '');
  return PATTERN.test(digits) ? digits : null;
}

export function formatNumber(n: string): string {
  return PATTERN.test(n) ? `${n.slice(0, 5)} ${n.slice(5)}` : n;
}
