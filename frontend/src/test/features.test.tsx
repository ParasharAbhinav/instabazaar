import React from 'react';
import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import {
  formatInr,
  isValidE164Phone,
  isValidPassword,
  normalizeSearchParams,
  toWhatsAppUrl,
} from '../utils/validation';
import { DEMO_PRODUCTS, IS_DEMO_ENABLED } from '../api/demo';
import * as UI from '../components/ui';

describe('InstaBazaar Storefront UI & Domain Security Suite', () => {
  it('formats INR prices with tabular precision and rupee symbol', () => {
    expect(formatInr('2499.00')).toContain('2,499');
    expect(formatInr(1499)).toContain('1,499');
  });

  it('returns fallback zero currency when given malformed numeric input', () => {
    expect(formatInr('not-a-number')).toBe('₹0.00');
  });

  it('enforces strict E.164 phone validation for seller WhatsApp handoff', () => {
    expect(isValidE164Phone('+919876543210')).toBe(true);
    expect(isValidE164Phone('+14155552671')).toBe(true);
    expect(isValidE164Phone('9876543210')).toBe(false);
    expect(isValidE164Phone('+91-98765-43210')).toBe(false);
    expect(isValidE164Phone('')).toBe(false);
  });

  it('constructs sanitized wa.me deep links with URL-encoded product context', () => {
    const url = toWhatsAppUrl('+919876543210', 'Archival Selvedge Denim Jacket');
    expect(url).toContain('https://wa.me/919876543210');
    expect(url).toContain(encodeURIComponent('Archival Selvedge Denim Jacket'));
  });

  it('validates password complexity boundaries for account registration', () => {
    expect(isValidPassword('StrongPass#2026')).toBe(true);
    expect(isValidPassword('short')).toBe(false);
  });

  it('normalizes catalog discovery query parameters and strips whitespace', () => {
    const params = normalizeSearchParams({
      q: '  handloom kurta ',
      minPrice: '500',
      maxPrice: '3500',
      page: 0,
    });
    expect(params.q).toBe('handloom kurta');
    expect(params.minPrice).toBe('500');
    expect(params.maxPrice).toBe('3500');
  });

  it('enforces production build guard disabling static demo fixtures', () => {
    expect(IS_DEMO_ENABLED).toBe(false);
    expect(DEMO_PRODUCTS).toHaveLength(0);
  });

  it('renders structured SkeletonCard loaders and LegalComplianceFooter (TOS & Privacy Policy)', () => {
    const skeletonHtml = renderToStaticMarkup(React.createElement(UI.SkeletonCard, { count: 3 }));
    expect(skeletonHtml).toContain('skeleton-grid');
    expect(skeletonHtml).toContain('aria-busy="true"');

    const legalHtml = renderToStaticMarkup(React.createElement(UI.LegalComplianceFooter));
    expect(legalHtml).toContain('Terms of Service');
    expect(legalHtml).toContain('Privacy Policy');
  });

  it('extracts searchable domain keywords from seller Reel transcripts and renders keyword chips', () => {
    const kws = UI.extractReelKeywordsFromTranscript(
      'Hey guys showing this vintage handloom chanderi silk kurta in olive green'
    );
    expect(kws).toContain('vintage');
    expect(kws).toContain('handloom');
    expect(kws).toContain('chanderi');
    expect(kws).toContain('kurta');

    const reelHtml = renderToStaticMarkup(
      React.createElement(UI.ReelKeywordExtractorCard, {
        initialTranscript: 'Archival selvedge denim workwear jacket',
      })
    );
    expect(reelHtml).toContain('Reel caption / transcript');
    expect(reelHtml).toContain('Instagram Reel link');
    expect(reelHtml).not.toContain('type="file"');
    expect(reelHtml).toContain('#selvedge');
    expect(reelHtml).toContain('#denim');
  });
});
