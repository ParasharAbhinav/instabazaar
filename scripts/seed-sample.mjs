#!/usr/bin/env node
// Seed an explicitly selected LOCAL development API through normal session/CSRF routes.
import { readFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { deflateSync } from 'node:zlib';

const args = process.argv.slice(2);
const base = (args.find(a => a.startsWith('--base-url='))?.split('=').slice(1).join('=') || 'http://localhost:8080/api/v1').replace(/\/$/, '');
const target = new URL(base);
if (!['localhost', '127.0.0.1', '[::1]'].includes(target.hostname) || !['http:', 'https:'].includes(target.protocol) || target.username || target.password) {
  throw new Error('Use a local development API on localhost or 127.0.0.1. Sample seeding is disabled for remote hosts.');
}
const data = JSON.parse(readFileSync(new URL('../frontend/src/sample/catalogue.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
const suppliedPassword = process.env.INSTABAZAAR_SAMPLE_PASSWORD;
const password = suppliedPassword || randomBytes(18).toString('base64url');
if (password.length < 12 || password.length > 128) throw new Error('Use a sample password of 12 to 128 characters.');
if (!suppliedPassword) console.log(`Generated password for NEW sample accounts: ${password}\nSave it locally for login and reruns; it is not written to disk.`);

class Api {
  cookies = new Map();
  csrf = null;
  async call(path, method = 'GET', body) {
    if (method !== 'GET' && !this.csrf) this.csrf = await this.call('/auth/csrf');
    const headers = { Accept: 'application/json', Cookie: [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; ') };
    if (method !== 'GET') headers[this.csrf.headerName] = this.csrf.token;
    if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body), signal: AbortSignal.timeout(30000), redirect: 'error' });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';', 1)[0]; const equal = pair.indexOf('=');
      this.cookies.set(pair.slice(0, equal), pair.slice(equal + 1));
    }
    const result = response.status === 204 ? null : await response.json();
    if (!response.ok) { const error = new Error(`${method} ${path}: ${result.message || response.status}`); error.status = response.status; throw error; }
    return result;
  }
  async account(account, role) {
    try { await this.call('/auth/register', 'POST', { ...account, password, role }); }
    catch (error) { if (error.status !== 409) throw error; }
    const user = await this.call('/auth/login', 'POST', { email: account.email, password });
    this.csrf = null; // Login rotates the token and session.
    if (user.role !== role) throw new Error('Existing sample account has an unexpected role.');
  }
}

// Deliberately simple colour-swatch placeholder PNGs, not product photography.
function swatch(red, green, blue) {
  const crc32 = bytes => { let crc = -1; for (const b of bytes) { crc ^= b; for (let k=0;k<8;k++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); } return (crc ^ -1) >>> 0; };
  const chunk = (type, bytes) => { const tag=Buffer.from(type); const size=Buffer.alloc(4); size.writeUInt32BE(bytes.length); const crc=Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([tag,bytes]))); return Buffer.concat([size,tag,bytes,crc]); };
  const width=256, height=256, row=width*3+1;
  const pixels=Buffer.alloc(row*height);
  for(let y=0;y<height;y++) for(let x=0;x<width;x++) {
    const offset=y*row+1+x*3;
    const border=x<12||y<12||x>243||y>243; const stripe=(x+y)%32<3;
    pixels[offset]=border?240:stripe?Math.min(255,red+25):red;
    pixels[offset+1]=border?238:stripe?Math.min(255,green+25):green;
    pixels[offset+2]=border?229:stripe?Math.min(255,blue+25):blue;
  }
  const info=Buffer.alloc(13); info.writeUInt32BE(width); info.writeUInt32BE(height,4); info[8]=8; info[9]=2;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',info),chunk('IDAT',deflateSync(pixels)),chunk('IEND',Buffer.alloc(0))]);
}

const seller = new Api();
await seller.account(data.seller, 'SELLER');
let store;
try { store = await seller.call('/seller/store'); }
catch (error) { if (error.status !== 404) throw error; }
if (!store) await seller.call('/seller/store', 'PUT', { ...data.store, logoAssetId: null, publicPhone: null, whatsappNumber: null, instagramUrl: null, facebookUrl: null, version: null });
const existing=[];
for (let page=0;;page++) { const batch=await seller.call(`/seller/products?page=${page}&size=100`); existing.push(...batch.items); if(page+1>=batch.totalPages) break; }
const ids=[];
const colours=[[104,115,75],[55,76,112],[170,95,67],[218,211,187],[163,111,125],[111,116,76]];
for (const [index, sample] of data.products.entries()) {
  let managed=existing.find(p => p.product.title===sample.title);
  if (!managed) {
    const upload=new FormData(); upload.set('file',new Blob([swatch(...colours[index])],{type:'image/png'}),`sample-swatch-${index+1}.png`);
    const asset=await seller.call('/seller/media','POST',upload);
    const { illustration, ...details }=sample;
    managed=await seller.call('/seller/products','POST',{...details,currency:'INR',availabilityEnabled:true,sourceUrl:null,images:[{assetId:asset.id,displayOrder:0,altText:'Sample colour swatch; not a product photograph'}]});
    managed=await seller.call(`/seller/products/${managed.product.id}/status`,'PATCH',{listingStatus:'ACTIVE',version:managed.product.version});
  }
  ids.push(managed.product.id);
  console.log(`Ready: ${sample.title}`);
}
const buyer = new Api();
await buyer.account(data.buyer,'BUYER');
await buyer.call(`/me/wishlist/${ids[0]}`,'PUT');
const reviews = await buyer.call('/me/reviews?page=0&size=100');
if (!reviews.items.some(r => r.productId === ids[0])) await buyer.call(`/products/${ids[0]}/reviews`,'POST',{rating:5,text:'Sample review for testing the moderation workflow.'});
console.log(`\nLocal sample ready: ${data.products.length} listings, seller, buyer, wishlist and pending review.`);
console.log(`Seller: ${data.seller.email}\nBuyer: ${data.buyer.email}`);
console.log('Use the generated password above or the password supplied in INSTABAZAAR_SAMPLE_PASSWORD.');
