const KEY = import.meta.env.VITE_GIPHY_API_KEY as string | undefined;
export const giphyConfigured = Boolean(KEY);

export interface Gif { id: string; url: string; preview: string }

/** Only https://media*.giphy.com URLs are accepted by database.rules.json. */
export async function searchGifs(q: string): Promise<Gif[]> {
  if (!KEY) return [];
  const endpoint = q.trim() ? 'search' : 'trending';
  const url = `https://api.giphy.com/v1/gifs/${endpoint}?api_key=${encodeURIComponent(KEY)}&limit=24&rating=g&q=${encodeURIComponent(q.trim())}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error('GIF search failed.');
  const json = await res.json();
  return (json.data as Array<{ id: string; images: { fixed_height: { url: string }; fixed_height_small: { url: string } } }>)
    .map((g) => ({ id: g.id, url: g.images.fixed_height.url, preview: g.images.fixed_height_small.url }))
    .filter((g) => /^https:\/\/media[0-9]*\.giphy\.com\//.test(g.url));
}
