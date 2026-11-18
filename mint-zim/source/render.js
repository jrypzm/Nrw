// Renders the Violet Quill icon for Zim from icon.html at 1024 px with a transparent background.
//   node render.js <out.png>
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1024, height: 1024 } });
  await p.goto('file://' + __dirname + '/icon.html?i=quill&p=violet');
  await p.screenshot({ path: process.argv[2], omitBackground: true });
  await b.close();
})();
