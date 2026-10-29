const KEY = import.meta.env.VITE_GIF_API_KEY as string | undefined;
export const gifApiConfigured = Boolean(KEY);

export interface Gif {
  id: string;
  url: string;
  preview: string;
}

export interface EmojiItem {
  symbol: string;
  name: string;
}

export async function searchGifs(q: string): Promise<Gif[]> {
  if (!KEY) return [];
  const query = encodeURIComponent(q.trim());
  const url = `https://api.yourprovider.com/v1/search?type=gif&query=${query}&key=${encodeURIComponent(KEY)}`;

  const res = await fetch(url);
  if (!res.ok) throw new Error('GIF search failed.');
  const json = await res.json();

  // Example response shape:
  // { data: [{ id, url, preview }] }
  const items = Array.isArray(json.data) ? json.data : [];
  return items
    .filter((g: any) => g?.url || g?.preview)
    .map((g: any) => ({
      id: String(g.id ?? Math.random().toString(36).slice(2)),
      url: String(g.url),
      preview: String(g.preview ?? g.url),
    }));
}

export async function fetchEmojiSet(): Promise<EmojiItem[]> {
  if (!KEY) return [];
  const url = `https://api.yourprovider.com/v1/emojis?key=${encodeURIComponent(KEY)}`;

  const res = await fetch(url);
  if (!res.ok) throw new Error('Emoji fetch failed.');
  const json = await res.json();

  // Example response:
  // { data: [{ symbol: '😀', name: 'grinning' }, ...] }
  const items = Array.isArray(json.data) ? json.data : [];
  return items
    .map((e: any) => ({
      symbol: String(e.symbol ?? e.emoji ?? ''),
      name: String(e.name ?? 'emoji'),
    }))
    .filter((e) => e.symbol);
}
