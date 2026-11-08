import test from "node:test";
import assert from "node:assert/strict";
import { PDFDocument, StandardFonts, degrees } from "pdf-lib";
import { brandCv, CV_LIMIT } from "../lib/account/cv";
test("CV branding preserves source bytes and adds a separate footer band to each rotated page", async () => {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  for (const rotation of [0, 90, 180, 270]) {
    const p = doc.addPage([400, 600]);
    p.setRotation(degrees(rotation));
    p.drawText("Original CV content", { x: 20, y: 15, font, size: 14 });
  }
  const bytes = await doc.save(),
    copy = bytes.slice();
  const result = await brandCv(bytes);
  assert.deepEqual(bytes, copy);
  const branded = await PDFDocument.load(result);
  assert.equal(branded.getPageCount(), 4);
  assert.deepEqual(
    branded.getPages().map((p) => p.getSize()),
    [
      { width: 400, height: 638 },
      { width: 600, height: 438 },
      { width: 400, height: 638 },
      { width: 600, height: 438 },
    ],
  );
  assert.equal((await PDFDocument.load(bytes)).getPage(0).getHeight(), 600);
});
test("CV limits reject non-PDF files, oversize files and excessive pages", async () => {
  await assert.rejects(() => brandCv(new TextEncoder().encode("not a PDF")));
  await assert.rejects(() => brandCv(new Uint8Array(CV_LIMIT + 1)));
  const doc = await PDFDocument.create();
  for (let i = 0; i < 41; i++) doc.addPage();
  await assert.rejects(async () => brandCv(await doc.save()));
});
