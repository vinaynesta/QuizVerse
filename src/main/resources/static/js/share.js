// "Share result": draws the score as an image on a <canvas> and hands it to the phone's share sheet
// (WhatsApp etc.), or downloads it where sharing files isn't supported (most desktops).

const W = 1080;
const H = 1080;

function wrap(ctx, text, maxWidth) {
  const lines = [];
  let line = "";
  for (const word of text.split(" ")) {
    const test = line ? `${line} ${word}` : word;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = word;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  return lines;
}

/**
 * result: { title, score, maxMarks, pct, correct, wrong, skipped, accuracy, streak }
 * Always light colours, so the image looks the same whatever theme the sharer uses.
 */
export function drawResultCard(result) {
  const canvas = document.createElement("canvas");
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext("2d");
  const font = (weight, size) => `${weight} ${size}px 'Segoe UI', system-ui, -apple-system, Roboto, sans-serif`;

  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, "#312e81");
  bg.addColorStop(1, "#4f46e5");
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  ctx.fillStyle = "#ffffff";
  ctx.beginPath();
  ctx.roundRect(60, 150, W - 120, H - 270, 40);
  ctx.fill();

  ctx.textAlign = "center";
  ctx.fillStyle = "#facc15";
  ctx.font = font(800, 56);
  ctx.fillText("QuizVerse", W / 2, 100);

  ctx.fillStyle = "#0f172a";
  ctx.font = font(700, 40);
  wrap(ctx, result.title, W - 220).slice(0, 2).forEach((l, i) => ctx.fillText(l, W / 2, 240 + i * 50));

  // Score ring
  const cx = W / 2, cy = 520, r = 160;
  ctx.lineWidth = 36;
  ctx.strokeStyle = "#e2e8f0";
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.stroke();
  ctx.strokeStyle = "#4f46e5";
  ctx.lineCap = "round";
  ctx.beginPath();
  ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + (Math.PI * 2 * Math.max(0.001, result.pct)) / 100);
  ctx.stroke();
  ctx.fillStyle = "#4338ca";
  ctx.font = font(800, 96);
  ctx.fillText(String(result.score), cx, cy + 20);
  ctx.fillStyle = "#64748b";
  ctx.font = font(600, 34);
  ctx.fillText(`out of ${result.maxMarks}`, cx, cy + 70);

  const stats = [
    [result.correct, "Correct", "#16a34a"],
    [result.wrong, "Wrong", "#dc2626"],
    [result.skipped, "Skipped", "#d97706"],
    [`${result.accuracy}%`, "Accuracy", "#4f46e5"],
  ];
  const colW = (W - 200) / stats.length;
  stats.forEach(([value, label, colour], i) => {
    const x = 100 + colW * i + colW / 2;
    ctx.fillStyle = colour;
    ctx.font = font(800, 56);
    ctx.fillText(String(value), x, 800);
    ctx.fillStyle = "#64748b";
    ctx.font = font(600, 28);
    ctx.fillText(label, x, 840);
  });

  ctx.fillStyle = "#ffffff";
  ctx.font = font(600, 34);
  const footer = result.streak > 1 ? `🔥 ${result.streak}-day streak · Practise free at QuizVerse` : "Practise bank exam papers free at QuizVerse";
  ctx.fillText(footer, W / 2, H - 50);
  return canvas;
}

/** Shares the card as a PNG, or downloads it. Resolves to "shared", "downloaded" or "cancelled". */
export async function shareResult(result) {
  const canvas = drawResultCard(result);
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
  const file = new File([blob], "quizverse-result.png", { type: "image/png" });
  const text = `I scored ${result.score}/${result.maxMarks} in ${result.title} on QuizVerse.`;
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], text });
      return "shared";
    } catch (error) {
      if (error.name === "AbortError") return "cancelled";
      // Some browsers refuse at the last step; fall back to downloading.
    }
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = file.name;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  return "downloaded";
}
