import type { Category } from '../api/contracts';

export type ListingFields = {
  title: string; description: string; categoryId: string; price: string;
  stockQuantity: string; colour: string; material: string; occasion: string;
  brand: string; sizes: string; sellerTags: string; sourceUrl: string; reelCaption: string;
};

export const SAMPLE_CATEGORIES: Category[] = [
  ['Dresses', 'dresses'], ['Kurtis', 'kurtis'], ['Ethnic Wear', 'ethnic-wear'],
  ['Casual Wear', 'casual-wear'], ['Tops', 'tops'], ['Other Garments', 'other-garments'],
].map(([name, slug], i) => ({
  id: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
  name, slug, description: null,
}));

export const SAMPLE_LISTING: ListingFields = {
  title: 'Olive Chanderi Silk Kurta',
  description: 'Olive green Chanderi silk kurta with hand block print detailing, a relaxed fit and a round neckline. Lightweight festive wear, available in S, M, L and XL. Hand wash separately in cold water. Sample listing for a fictional independent store.',
  categoryId: SAMPLE_CATEGORIES[1].id,
  price: '2499.00', stockQuantity: '12', colour: 'olive green', material: 'Chanderi silk',
  occasion: 'Festive', brand: 'InstaBazaar Sample Studio', sizes: 'S, M, L, XL', sellerTags: '',
  sourceUrl: '',
  reelCaption: 'Hand block print olive green Chanderi silk kurta for festive gatherings. Relaxed fit in sizes S to XL. Hand wash in cold water. #handloom #chanderi #festivewear',
};

const stopWords = new Set(('the and for with this that from have has are was were will your our very just like new reel video dm whatsapp price shipping available everyone hey showing piece wear in to a an of is it on at or as by into separately sample listing fictional store studio sizes size wash water cold hand detailing fit neckline round lightweight gatherings').split(' '));
const vocabulary = new Set(('handloom kurta saree chanderi banarasi linen cotton silk khadi denim selvedge vintage thrift archival oversized corduroy indigo terracotta embroidered blockprint chikankari kalamkari jacket trousers shirt handwoven unisex deadstock streetwear workwear flannel wool festive casual olive green relaxed').split(' '));

/** Local text suggestions only: no scraping, audio transcription, or inferred facts. */
export function extractListingKeywords(text: string, limit = 12): string[] {
  const normalized = text.normalize('NFKC').toLocaleLowerCase()
    .replace(/https?:\/\/\S+/g, ' ')
    .replace(/block[ -]+print/g, 'blockprint').replace(/hand[ -]+woven/g, 'handwoven');
  const tokens = normalized.match(/[\p{L}\p{M}][\p{L}\p{M}\p{N}-]*/gu) || [];
  const candidates = [...new Set(tokens)].filter(t => t.length >= 3 && t.length <= 60 && !stopWords.has(t));
  return [...candidates.filter(t => vocabulary.has(t)), ...candidates.filter(t => !vocabulary.has(t))]
    .slice(0, Math.max(0, Math.min(limit, 20)));
}

export function suggestListingKeywords(fields: ListingFields, category = '') {
  return extractListingKeywords([
    fields.title, fields.colour, fields.material, fields.occasion, category,
    fields.brand, fields.description, fields.reelCaption,
  ].join(' '));
}

export function mergeListingKeywords(existing: string, additions: string[], excluded: string[] = []) {
  const blocked = new Set(excluded.map(t => t.trim().toLocaleLowerCase()));
  const unique = new Map<string, string>();
  for (const raw of [...existing.split(','), ...additions]) {
    const tag = raw.trim();
    const key = tag.toLocaleLowerCase();
    if (tag && tag.length <= 60 && !blocked.has(key) && !unique.has(key)) unique.set(key, tag);
  }
  return [...unique.values()].slice(0, 20).join(', ');
}

export function safeReelUrl(value: string): string | null {
  if (!value.trim()) return null;
  try {
    const url = new URL(value.trim());
    if (url.protocol !== 'https:' || !['instagram.com', 'www.instagram.com'].includes(url.hostname)
      || url.username || url.password || (url.port && url.port !== '443')
      || !/^\/reels?\/[A-Za-z0-9_-]+\/?$/.test(url.pathname)) return null;
    return `https://www.instagram.com${url.pathname.replace(/\/?$/, '/')}`;
  } catch { return null; }
}
