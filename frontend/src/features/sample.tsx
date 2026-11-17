import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Editor } from './seller';
import catalogue from '../sample/catalogue.json';
import { money } from '../utils/validation';

export function SampleProject() {
  const [view, setView] = useState<'catalogue' | 'seller'>('seller');
  const [search, setSearch] = useState('');
  const products = catalogue.products.filter(p =>
    [p.title, p.description, ...p.sellerTags].join(' ').toLocaleLowerCase().includes(search.toLocaleLowerCase().trim()));
  return <>
    <section className="sample-intro">
      <div><span className="eyebrow">EXPLORE INSTABAZAAR</span><h1>A complete sample, ready to try.</h1>
        <p>Six fictional listings, a sample storefront, and the real seller editor with Reel links and keyword suggestions.</p></div>
      <div className="sample-mode-note"><strong>Sample workspace</strong><p>Try the form without signing in. Preview changes stay on this page. Sign in to save real listings.</p><Link to="/register">Create a seller account →</Link></div>
    </section>
    <nav className="sample-tabs" aria-label="Sample workspace views">
      <button type="button" aria-pressed={view === 'seller'} onClick={() => setView('seller')}>Seller editor + Reel</button>
      <button type="button" aria-pressed={view === 'catalogue'} onClick={() => setView('catalogue')}>Sample storefront · 6 products</button>
    </nav>
    {view === 'seller' ? <Editor sampleMode /> : <section className="section">
      <div className="section-heading"><div><span className="eyebrow">FICTIONAL STOREFRONT</span><h2>{catalogue.store.storeName}</h2><p>{catalogue.store.description}</p></div></div>
      <label className="field"><span>Search sample titles, descriptions and keywords</span><input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Try chanderi, denim, linen…" /></label>
      <p role="status">{products.length} sample products</p>
      <div className="sample-catalogue-grid">{products.map(p => <article className="sample-product" key={p.title}>
        <img src={'/images/' + p.illustration} alt={'Illustrative placeholder for ' + p.title} loading="lazy" />
        <div><span className="eyebrow">SAMPLE · {p.occasion}</span><h3>{p.title}</h3><strong>{money(p.price)}</strong><p>{p.description}</p>
          <small>{p.stockQuantity} in stock · {p.sizes.join(', ')}</small><div className="keyword-chips">{p.sellerTags.map(tag => <span className="badge" key={tag}>#{tag}</span>)}</div></div>
      </article>)}</div>
    </section>}
    <section className="panel sample-next"><h2>Try the full project locally</h2><p>The supplied seed script creates a sample seller, buyer, storefront, six public products, a saved item and a pending review through the real API. Follow SAMPLE-GUIDE.md in the project folder.</p><Link to="/login">Sign in to the connected backend →</Link></section>
  </>;
}
