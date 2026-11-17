import { useState } from 'react';
import { Clapperboard, ExternalLink, Sparkles } from 'lucide-react';
import { mergeListingKeywords, safeReelUrl, suggestListingKeywords, type ListingFields } from '../utils/listing';
import { safeSocial } from '../utils/validation';

export function ReelKeywordExtractorCard({ fields, onChange, category = '', excluded = [], initialTranscript = '', originalSourceUrl }: {
  fields?: ListingFields;
  onChange?: (patch: Partial<ListingFields>) => void;
  category?: string;
  excluded?: string[];
  initialTranscript?: string;
  originalSourceUrl?: string | null;
}) {
  const [caption, setCaption] = useState(initialTranscript);
  const [message, setMessage] = useState('');
  const current = fields ?? { title: '', description: '', categoryId: '', price: '', stockQuantity: '0',
    colour: '', material: '', occasion: '', brand: '', sizes: '', sellerTags: '', sourceUrl: '', reelCaption: caption };
  const keywords = suggestListingKeywords(current, category).filter(t => !excluded.includes(t));
  const selected = new Set(current.sellerTags.split(',').map(t => t.trim().toLocaleLowerCase()));
  const reel = safeReelUrl(current.sourceUrl);
  const legacySource = !reel && current.sourceUrl === originalSourceUrl ? safeSocial(current.sourceUrl) : null;
  const change = (patch: Partial<ListingFields>) => { onChange?.(patch); setMessage(''); };
  return <section className="listing-assistant" aria-label="Reel and keyword assistant">
    <span className="eyebrow"><Clapperboard size={16} /> REEL & DISCOVERY</span>
    <h2>Add the story behind it.</h2>
    <p>Link your Instagram Reel and paste its caption or transcript. Keywords also come from the complete product details on the left.</p>
    <label className="field">
      <span>Instagram Reel link (optional)</span>
      <input name="sourceUrl" type="url" maxLength={2048} placeholder="https://www.instagram.com/reel/…/"
        value={current.sourceUrl} onChange={e => change({ sourceUrl: e.target.value })}
        aria-invalid={!!current.sourceUrl && !reel && !legacySource} aria-describedby="reel-link-help" />
    </label>
    <small id="reel-link-help">Use an HTTPS instagram.com/reel/… link. A link does not download or transcribe the Reel.</small>
    {current.sourceUrl && !reel && !legacySource && <p className="notice error" role="alert">Enter a valid Instagram Reel link.</p>}
    {legacySource && <p className="assistant-help">Existing social listing link retained. Replace it with a Reel link to switch.</p>}
    {reel && <a className="reel-link" href={reel} target="_blank" rel="noopener noreferrer">Open Reel on Instagram <ExternalLink size={15} /></a>}
    <label className="field">
      <span>Reel caption / transcript (optional)</span>
      <textarea name="reelCaption" maxLength={2000} rows={5} value={current.reelCaption}
        placeholder="Paste the caption or write what is said in the Reel…"
        onChange={e => { setCaption(e.target.value); change({ reelCaption: e.target.value }); }} />
    </label>
    <small>{current.reelCaption.length}/2000 · Saved privately with your listing.</small>
    <hr />
    <h3><Sparkles size={17} /> Suggested buyer keywords</h3>
    <p className="assistant-help">Suggestions update as you write. Select keywords to add them to your editable tags before saving.</p>
    <div className="keyword-chips" aria-label="Extracted search keywords">
      {keywords.map(keyword => <button key={keyword} type="button" className={selected.has(keyword) ? 'keyword selected' : 'keyword'}
        aria-pressed={selected.has(keyword)} onClick={() => change({ sellerTags: selected.has(keyword)
          ? current.sellerTags.split(',').filter(t => t.trim().toLocaleLowerCase() !== keyword).join(', ')
          : mergeListingKeywords(current.sellerTags, [keyword], excluded) })}>#{keyword}</button>)}
    </div>
    {!keywords.length && <p className="assistant-help">Write a title, description, material, or caption to see suggestions. A Reel is optional.</p>}
    <button type="button" disabled={!keywords.length || !onChange} onClick={() => {
      change({ sellerTags: mergeListingKeywords(current.sellerTags, keywords, excluded) });
      setMessage('Suggested keywords added to your tags. Review them before saving.');
    }}><Sparkles size={16} /> Add suggested keywords</button>
    {message && <p role="status" className="assistant-help">{message}</p>}
  </section>;
}
