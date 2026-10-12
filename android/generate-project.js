// Generates the Android (Trusted Web Activity) project from android/twa-manifest.json
// with Bubblewrap (the tool Google recommends for turning a web app into an Android app).
// Usage: node generate-project.js <path to twa-manifest.json> <output folder>
const fs = require('fs');
const path = require('path');
const { TwaManifest, TwaGenerator, ConsoleLog } = require('@bubblewrap/core');

(async () => {
    const [manifestPath, outDir] = process.argv.slice(2);
    const manifest = await TwaManifest.fromFile(manifestPath);
    const errors = manifest.validate();
    if (errors) throw new Error('twa-manifest.json: ' + errors);
    fs.mkdirSync(outDir, { recursive: true });
    await new TwaGenerator().createTwaProject(outDir, manifest, new ConsoleLog('generate'));
    fs.copyFileSync(manifestPath, path.join(outDir, 'twa-manifest.json'));
    console.log('Project generated in ' + outDir);
})().catch((e) => { console.error(e); process.exit(1); });
