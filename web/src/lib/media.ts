/** Photos and videos are NOT stored in Firebase. They are uploaded to a free public file host (catbox.moe by default)
 *  and only the resulting link is stored in the chat. Anyone with the link can open the file. */

const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env ?? {};

export const MAX_UPLOAD_MB = Number(env.VITE_MAX_UPLOAD_MB) || 200;
export const MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1000 * 1000;
/** Default: catbox's public API. Set VITE_UPLOAD_URL to your own proxy if the browser is blocked (see README). */
export const UPLOAD_ENDPOINT = env.VITE_UPLOAD_URL || 'https://catbox.moe/user/api.php';

const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const VIDEO_TYPES = ['video/mp4', 'video/webm', 'video/quicktime'];

export function mediaKind(type: string): 'image' | 'video' | null {
  if (IMAGE_TYPES.includes(type)) return 'image';
  if (VIDEO_TYPES.includes(type)) return 'video';
  return null;
}

/** Returns an error message, or null when the file can be sent. Mirrors the Android app. */
export function validateMedia(file: { type: string; size: number }, maxBytes = MAX_UPLOAD_BYTES): string | null {
  if (!file.size) return 'This file is empty.';
  if (!mediaKind(file.type)) return 'Only photos (JPEG, PNG, WebP, GIF) and videos (MP4, WebM, MOV) can be sent.';
  if (file.size > maxBytes) return `This file is larger than ${Math.round(maxBytes / 1_000_000)} MB.`;
  return null;
}

/** Same pattern as database.rules.json. */
export const HOSTED_URL = /^https:\/\/files\.catbox\.moe\/[A-Za-z0-9_-]+\.[A-Za-z0-9]{2,5}$/;

export class UploadError extends Error {}

/** The host answers with the plain file URL on success and an error sentence otherwise. */
export function parseUploadResponse(text: string): string {
  const url = text.trim();
  if (HOSTED_URL.test(url)) return url;
  throw new UploadError(url && url.length < 200 ? `The file host refused the upload: ${url}` : 'The file host sent an unexpected answer.');
}

export function safeUploadName(name: string): string {
  const clean = name.replace(/[^\w.-]+/g, '_').slice(-80);
  return /\.[A-Za-z0-9]{2,5}$/.test(clean) ? clean : `${clean || 'file'}.bin`;
}

export function uploadMedia(file: File, onProgress: (fraction: number) => void): Promise<string> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', UPLOAD_ENDPOINT);
    xhr.timeout = 20 * 60 * 1000;
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(e.loaded / e.total); };
    xhr.onload = () => {
      try {
        if (xhr.status < 200 || xhr.status >= 300) throw new UploadError(`The file host answered with an error (${xhr.status}).`);
        resolve(parseUploadResponse(xhr.responseText));
      } catch (e) { reject(e); }
    };
    xhr.onerror = () => reject(new UploadError('Upload failed. The file host could not be reached, or it blocked this website. Check your connection; if it keeps failing see the README section "If...');
    xhr.ontimeout = () => reject(new UploadError('The upload took too long. Try a smaller file or a faster connection.'));
    const form = new FormData();
    form.append('reqtype', 'fileupload');
    form.append('fileToUpload', file, safeUploadName(file.name));
    xhr.send(form);
  });
}
