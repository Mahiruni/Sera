import { ImageResponse } from "next/og";
export const alt = "Sera.et — opportunities, education and practical guidance";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";
export default function Image() {
  return new ImageResponse(
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        width: "100%",
        height: "100%",
        padding: "76px",
        background: "#e9f7fc",
        color: "#172d3b",
        fontFamily: "sans-serif",
      }}
    >
      <div style={{ display: "flex", fontSize: 58, fontWeight: 700 }}>
        Sera<span style={{ color: "#087f9f" }}>.et</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
        <div
          style={{
            display: "flex",
            fontSize: 68,
            fontWeight: 700,
            maxWidth: 1000,
            lineHeight: 1.1,
          }}
        >
          A clearer direction for your next move.
        </div>
        <div style={{ display: "flex", fontSize: 27, color: "#476a7d" }}>
          Opportunities · Education · Practical guides for Ethiopia
        </div>
      </div>
      <div style={{ display: "flex", fontSize: 20, color: "#52778a" }}>
        Public guidance available now. Live services are not launched.
      </div>
    </div>,
    size,
  );
}
