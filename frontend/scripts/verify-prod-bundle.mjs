import fs from 'node:fs';
import path from 'node:path';

if (process.env.VITE_DEMO_MODE === 'true') {
  console.error('FATAL: Production build aborted because VITE_DEMO_MODE=true is set.');
  process.exit(1);
}

const distDir = path.resolve(process.cwd(), 'dist', 'assets');
if (!fs.existsSync(distDir)) {
  console.error('FATAL: dist/assets directory does not exist.');
  process.exit(1);
}

const jsFiles = fs.readdirSync(distDir).filter((f) => f.endsWith('.js'));
if (jsFiles.length === 0) {
  console.error('FATAL: No .js files emitted in dist/assets.');
  process.exit(1);
}

// Real fixture strings from the old demo.ts that MUST NEVER appear in production bundles
const FORBIDDEN_FIXTURE_STRINGS = [
  'The Loom Story',
  'Archival Selvedge',
  'handloom-kurta-demo',
  'demo-seller'
];

for (const file of jsFiles) {
  const content = fs.readFileSync(path.join(distDir, file), 'utf8');
  for (const forbidden of FORBIDDEN_FIXTURE_STRINGS) {
    if (content.includes(forbidden)) {
      console.error(`FATAL: Demo fixture string "${forbidden}" leaked into production chunk ${file}!`);
      process.exit(1);
    }
  }
}

console.log(`Verified no obsolete demo fixtures across ${jsFiles.length} production chunks. The explicit /sample workspace is permitted.`);
