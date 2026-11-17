import { describe, expect, it } from 'vitest';
import { extractListingKeywords, mergeListingKeywords, safeReelUrl, SAMPLE_LISTING, suggestListingKeywords } from '../utils/listing';

describe('listing keyword and Reel workflow', () => {
  it('suggests keywords from complete product details without a Reel', () => {
    const keywords = suggestListingKeywords({ ...SAMPLE_LISTING, reelCaption: '', sourceUrl: '' });
    expect(keywords).toEqual(expect.arrayContaining(['chanderi', 'silk', 'kurta', 'olive', 'blockprint']));
  });
  it('handles hashtags, duplicate words and Hindi without inventing a transcript', () => {
    const keywords = extractListingKeywords('https://example.com/fake #Cotton cotton हाथकरघा सूती कुर्ता');
    expect(keywords.filter(t => t === 'cotton')).toHaveLength(1);
    expect(keywords).toContain('हाथकरघा');
    expect(keywords).not.toContain('fake');
    expect(extractListingKeywords('')).toEqual([]);
  });
  it('adds suggestions without losing manual tags or duplicating system tags', () => {
    expect(mergeListingKeywords('Custom style, SILK', ['silk', 'kurta', 'handloom'], ['handloom']))
      .toBe('Custom style, SILK, kurta');
    expect(mergeListingKeywords('', Array.from({ length: 30 }, (_, i) => 'tag' + i)).split(',')).toHaveLength(20);
  });
  it('accepts only real Instagram Reel URL shapes and removes tracking parameters', () => {
    expect(safeReelUrl('https://instagram.com/reel/Abc_123/?igsh=secret')).toBe('https://www.instagram.com/reel/Abc_123/');
    for (const value of ['http://instagram.com/reel/abc/', 'https://instagram.com.attacker.test/reel/abc/',
      'https://user@instagram.com/reel/abc/', 'https://instagram.com:444/reel/abc/', 'https://instagram.com/profile/', 'javascript:alert(1)']) {
      expect(safeReelUrl(value)).toBeNull();
    }
  });
  it('limits suggestions to the editable tag budget and excludes generic filler', () => {
    const keywords = extractListingKeywords('Hey everyone this is our available cotton kurta for shipping ' + Array.from({length: 40}, (_, i) => 'word' + i).join(' '));
    expect(keywords.length).toBeLessThanOrEqual(12);
    expect(keywords).not.toContain('shipping');
    expect(keywords).toEqual(expect.arrayContaining(['cotton', 'kurta']));
  });
});
