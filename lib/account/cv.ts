import { PDFDocument, StandardFonts, rgb, degrees } from "pdf-lib";
export const CV_LIMIT = 3 * 1024 * 1024;
export async function brandCv(bytes: Uint8Array) {
  if (
    bytes.length > CV_LIMIT ||
    Buffer.from(bytes.subarray(0, 5)).toString() !== "%PDF-"
  )
    throw Error("Use a PDF no larger than 3 MB.");
  const original = await PDFDocument.load(bytes, { updateMetadata: false });
  const pages = original.getPages();
  if (!pages.length || pages.length > 40)
    throw Error("Branded copies support up to 40 pages.");
  const output = await PDFDocument.create();
  const font = await output.embedFont(StandardFonts.Helvetica);
  for (let i = 0; i < pages.length; i++) {
    const source = pages[i];
    const embedded = await output.embedPage(source);
    const w = embedded.width,
      h = embedded.height;
    const rotation = ((source.getRotation().angle % 360) + 360) % 360;
    if (
      ![0, 90, 180, 270].includes(rotation) ||
      Math.max(w, h) > 3000 ||
      Math.min(w, h) < 200
    )
      throw Error("This page layout needs a different PDF export.");
    const sideways = rotation === 90 || rotation === 270;
    const width = sideways ? h : w,
      height = sideways ? w : h;
    const page = output.addPage([width, height + 38]);
    const pos =
      rotation === 90
        ? { x: 0, y: w + 38 }
        : rotation === 180
          ? { x: w, y: h + 38 }
          : rotation === 270
            ? { x: h, y: 38 }
            : { x: 0, y: 38 };
    page.drawPage(embedded, { ...pos, rotate: degrees(-rotation) });
    page.drawRectangle({
      x: 0,
      y: 0,
      width,
      height: 38,
      color: rgb(0.96, 0.98, 0.99),
    });
    page.drawLine({
      start: { x: 18, y: 37 },
      end: { x: width - 18, y: 37 },
      color: rgb(0.78, 0.87, 0.9),
      thickness: 0.5,
    });
    page.drawText("Sera.et | Professional Profile", {
      x: 18,
      y: 22,
      size: 8,
      font,
      color: rgb(0.03, 0.4, 0.49),
    });
    page.drawText("sera-dusky-seven.vercel.app", {
      x: 18,
      y: 10,
      size: 7,
      font,
      color: rgb(0.36, 0.44, 0.48),
    });
    const number = `${i + 1} / ${pages.length}`;
    page.drawText(number, {
      x: width - 18 - font.widthOfTextAtSize(number, 8),
      y: 17,
      size: 8,
      font,
      color: rgb(0.36, 0.44, 0.48),
    });
  }
  output.setTitle("Sera professional profile - branded copy");
  output.setCreator("Sera.et");
  return output.save();
}
