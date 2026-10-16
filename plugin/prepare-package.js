// Public distribution sources must be readable by a nonroot gateway service.
import { chmodSync, readFileSync } from 'node:fs';
const manifest = JSON.parse(readFileSync(new URL('./package.json', import.meta.url)));
for (const file of [...manifest.files, 'package.json']) {
  chmodSync(new URL(file, import.meta.url), 0o644);
}
