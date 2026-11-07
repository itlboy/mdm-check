// mdm-check.cozypi.com — download page for the Cozy MDM Check Android app.
// The latest release is read from GitHub (cached 5 min), so publishing a new APK needs no redeploy.
//   /            landing page (Vietnamese or English from Accept-Language, ?lang=vi|en overrides)
//   /download    302 to the newest .apk
//   /latest.json { version, date, size, url } for scripts and the app

const REPO = "itlboy/mdm-check";
const RELEASES = `https://github.com/${REPO}/releases/latest`;
const CACHE_SECONDS = 300;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method not allowed", { status: 405, headers: { Allow: "GET, HEAD" } });
    }

    if (url.pathname === "/download") {
      const rel = await latest(ctx);
      return Response.redirect(rel?.url || RELEASES, 302);
    }
    if (url.pathname === "/latest.json") {
      const rel = await latest(ctx);
      return new Response(JSON.stringify(rel ?? { error: "unavailable" }), {
        status: rel ? 200 : 503,
        headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": `public, max-age=${CACHE_SECONDS}` },
      });
    }
    if (url.pathname === "/icon.svg") {
      return new Response(ICON_SVG, {
        headers: { "Content-Type": "image/svg+xml", "Cache-Control": "public, max-age=86400" },
      });
    }
    if (url.pathname !== "/") {
      return Response.redirect(new URL("/", url).toString(), 301);
    }

    const lang = pickLang(url, request);
    const rel = await latest(ctx);
    return new Response(page(lang, rel), {
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "public, max-age=60",
        "Content-Language": lang,
        Vary: "Accept-Language",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "strict-origin-when-cross-origin",
      },
    });
  },
};

/** Latest release info, cached at the edge. Returns null if GitHub is unreachable. */
async function latest(ctx) {
  const cache = caches.default;
  const key = new Request("https://mdm-check.cozypi.com/__cache/latest");
  const hit = await cache.match(key);
  if (hit) return hit.json();

  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
      headers: { Accept: "application/vnd.github+json", "User-Agent": "cozy-mdm-check-site" },
    });
    if (!r.ok) return null;
    const j = await r.json();
    const apk = (j.assets || []).find((a) => a.name.endsWith(".apk"));
    if (!apk) return null;
    const data = {
      version: String(j.tag_name || "").replace(/^v/i, ""),
      date: j.published_at,
      size: apk.size,
      url: apk.browser_download_url,
    };
    const res = new Response(JSON.stringify(data), {
      headers: { "Content-Type": "application/json", "Cache-Control": `public, max-age=${CACHE_SECONDS}` },
    });
    ctx.waitUntil(cache.put(key, res));
    return data;
  } catch {
    return null;
  }
}

function pickLang(url, request) {
  const q = url.searchParams.get("lang");
  if (q === "vi" || q === "en") return q;
  const al = (request.headers.get("Accept-Language") || "").toLowerCase();
  return al.startsWith("vi") || /(^|,)\s*vi\b/.test(al) ? "vi" : "en";
}

const esc = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

function fmtDate(iso, lang) {
  const d = new Date(iso);
  if (isNaN(d)) return "";
  if (lang === "vi") {
    const p = (n) => String(n).padStart(2, "0");
    return `${p(d.getUTCDate())}/${p(d.getUTCMonth() + 1)}/${d.getUTCFullYear()}`;
  }
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
}

function fmtSize(bytes, lang) {
  if (!bytes) return "";
  const mb = (bytes / 1024 / 1024).toFixed(1);
  return `${lang === "vi" ? mb.replace(".", ",") : mb} MB`;
}

const T = {
  vi: {
    title: "Cozy MDM Check — kiểm tra máy Android có bị MDM",
    desc: "Bấm một nút để biết điện thoại Android có bị MDM, hồ sơ công việc hay khoá trả góp không. Miễn phí, chạy ngay trên máy.",
    tagline: "Bấm một nút để biết điện thoại có bị <b>MDM</b>, <b>hồ sơ công việc</b> hay <b>khoá trả góp</b> không.",
    download: "Tải APK",
    version: "Phiên bản",
    unavailable: "Xem các bản phát hành",
    req: "Android 8 trở lên",
    whatTitle: "Kiểm tra những gì",
    features: [
      ["shield", "Dấu hiệu quản lý", "App quản trị thiết bị, máy của tổ chức, hồ sơ công việc, app MDM / khoá trả góp, tính năng bị chặn."],
      ["phone", "Thông tin máy", "Tên thương mại, mã model, phiên bản Android, bản vá bảo mật."],
      ["clock", "Lịch sử máy", "Ngày thiết lập máy: biết máy đã qua tay người khác hay chưa."],
      ["lock", "Bảo mật khác", "Bootloader, root, tuỳ chọn nhà phát triển, gỡ lỗi USB."],
    ],
    stepsTitle: "Cài trong 3 bước",
    steps: [
      ["Tải file APK", "Bấm nút Tải APK ở trên, ngay trên điện thoại cần kiểm tra."],
      ["Mở file để cài", "Nếu Google Play Protect hỏi, chọn <b>Quét ứng dụng</b> rồi cài tiếp. App mới nên Google chưa biết, không phải lỗi."],
      ["Bấm Kiểm tra", "Mở app, bấm nút tròn. Kết quả hiện ngay, không gửi đi đâu."],
    ],
    noteTitle: "Cần biết",
    note: "App không thấy được MDM gắn theo IMEI (zero-touch), loại chỉ kích hoạt sau khi reset. Muốn chắc chắn khi mua máy: khôi phục cài đặt gốc rồi thiết lập lại có Wi‑Fi.",
    updates: "App tự báo khi có bản mới — chấm đỏ ở cuối màn hình, chạm vào là tải.",
    source: "Mã nguồn trên GitHub",
    other: "English",
  },
  en: {
    title: "Cozy MDM Check — is this Android phone under MDM?",
    desc: "Tap one button to see whether an Android phone is under MDM, has a work profile or a payment lock. Free, runs on the phone.",
    tagline: "Tap one button to see whether a phone is under <b>MDM</b>, has a <b>work profile</b> or a <b>payment lock</b>.",
    download: "Download APK",
    version: "Version",
    unavailable: "See all releases",
    req: "Android 8+",
    whatTitle: "What it checks",
    features: [
      ["shield", "Management signs", "Device admin apps, organization-owned device, work profile, MDM / payment-lock apps, blocked features."],
      ["phone", "Device info", "Marketing name, model code, Android version, security patch."],
      ["clock", "Device history", "Setup date: tells whether the phone has been used before."],
      ["lock", "Other security", "Bootloader, root, developer options, USB debugging."],
    ],
    stepsTitle: "Install in 3 steps",
    steps: [
      ["Download the APK", "Tap Download APK above, on the phone you want to check."],
      ["Open it to install", "If Google Play Protect asks, choose <b>Scan app</b> and continue. The app is new to Google; this is not an error."],
      ["Tap Check", "Open the app and tap the round button. Results appear instantly and never leave the phone."],
    ],
    noteTitle: "Good to know",
    note: "The app cannot see IMEI-bound MDM (zero-touch), which only kicks in after a reset. To be sure when buying: factory reset, then set the phone up on Wi‑Fi.",
    updates: "The app tells you when a new version is out — a red dot at the bottom, tap to download.",
    source: "Source on GitHub",
    other: "Tiếng Việt",
  },
};

const ICONS = {
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  phone: '<path d="M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z"/><path d="M11 18h2"/>',
  clock: '<path d="M12 7v5l3 2"/><circle cx="12" cy="12" r="9"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  download: '<path d="M12 3v12"/><path d="M7 10l5 5 5-5"/><path d="M5 21h14"/>',
  github: '<path d="M9 19c-4.3 1.4-4.3-2.5-6-3m12 5v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12.3 12.3 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21"/>',
};
const svg = (name, size = 24) =>
  `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;

const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="#2563EB"/><g transform="translate(30 30) scale(2)" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></g></svg>`;

const CHIP = { shield: ["#DCFCE7", "#15803D"], phone: ["#DBEAFE", "#1D4ED8"], clock: ["#FEF3C7", "#B45309"], lock: ["#EDE9FE", "#6D28D9"] };

function page(lang, rel) {
  const t = T[lang];
  const other = lang === "vi" ? "en" : "vi";
  const meta = rel
    ? [`${t.version} ${esc(rel.version)}`, fmtSize(rel.size, lang), t.req, fmtDate(rel.date, lang)].filter(Boolean).join(" · ")
    : t.req;
  const button = rel
    ? `<a class="btn" href="/download">${svg("download", 22)}<span>${t.download}</span></a>`
    : `<a class="btn" href="${RELEASES}">${svg("download", 22)}<span>${t.unavailable}</span></a>`;

  const features = t.features
    .map(([icon, title, text]) => {
      const [bg, fg] = CHIP[icon];
      return `<li class="card"><span class="chip" style="background:${bg};color:${fg}">${svg(icon)}</span><div><h3>${title}</h3><p>${text}</p></div></li>`;
    })
    .join("");
  const steps = t.steps.map(([title, text], i) => `<li><span class="num">${i + 1}</span><div><h3>${title}</h3><p>${text}</p></div></li>`).join("");

  return `<!doctype html>
<html lang="${lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${t.title}</title>
<meta name="description" content="${t.desc}">
<meta name="theme-color" content="#F4F6F9">
<meta property="og:title" content="Cozy MDM Check">
<meta property="og:description" content="${t.desc}">
<meta property="og:type" content="website">
<meta property="og:url" content="https://mdm-check.cozypi.com/">
<link rel="icon" href="/icon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/icon.svg">
<link rel="alternate" hreflang="vi" href="https://mdm-check.cozypi.com/?lang=vi">
<link rel="alternate" hreflang="en" href="https://mdm-check.cozypi.com/?lang=en">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
:root{--bg:#F4F6F9;--ink:#111827;--muted:#4B5563;--line:#E5E7EB;--blue:#2563EB;--blue-d:#1D4ED8;--card:rgba(255,255,255,.86);color-scheme:light}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);font-family:Inter,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;line-height:1.5;position:relative;overflow-x:hidden}
/* Modern backdrop: soft colour mesh at the top + a dot grid that fades out. Pure CSS, no images. */
.bg{position:absolute;inset:0 0 auto 0;height:900px;z-index:-1;pointer-events:none;overflow:hidden}
.bg::before{content:"";position:absolute;inset:0;background:
  radial-gradient(38rem 26rem at 50% -6%,rgba(37,99,235,.28),transparent 62%),
  radial-gradient(24rem 20rem at 92% 8%,rgba(139,92,246,.22),transparent 62%),
  radial-gradient(22rem 18rem at 4% 22%,rgba(20,184,166,.20),transparent 62%)}
.bg::after{content:"";position:absolute;inset:0;
  background-image:radial-gradient(rgba(17,24,39,.10) 1px,transparent 1.2px);background-size:22px 22px;
  -webkit-mask-image:linear-gradient(to bottom,#000 0%,transparent 75%);mask-image:linear-gradient(to bottom,#000 0%,transparent 75%)}
.card,ol.steps{-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px)}
a{color:var(--blue)}
.wrap{max-width:720px;margin:0 auto;padding:0 20px}
header.top{display:flex;align-items:center;justify-content:space-between;padding:16px 0}
.brand{display:flex;align-items:center;gap:10px;font-weight:700;color:var(--ink);text-decoration:none}
.brand img{width:32px;height:32px;border-radius:9px}
.lang{font-size:14px;font-weight:600;color:var(--muted);text-decoration:none;padding:8px 12px;border-radius:999px;background:#fff;border:1px solid var(--line)}
.hero{text-align:center;padding:28px 0 8px}
.hero .icon{width:96px;height:96px;border-radius:26px;box-shadow:0 0 0 12px rgba(37,99,235,.12),0 14px 30px rgba(37,99,235,.28)}
h1{font-size:clamp(32px,8vw,44px);line-height:1.1;letter-spacing:-.02em;margin:28px 0 12px;font-weight:800}
.tag{font-size:18px;color:var(--muted);margin:0 auto;max-width:30ch}
.tag b{color:var(--ink);font-weight:600}
.btn{display:flex;align-items:center;justify-content:center;gap:10px;margin:28px auto 0;max-width:360px;min-height:60px;border-radius:18px;background:var(--blue);color:#fff;font-size:19px;font-weight:700;text-decoration:none;box-shadow:0 10px 24px rgba(37,99,235,.3);transition:background .15s,transform .15s}
.btn:hover{background:var(--blue-d)}
.btn:active{transform:scale(.98)}
.btn:focus-visible,.lang:focus-visible{outline:3px solid #93C5FD;outline-offset:3px}
.meta{margin:12px 0 0;font-size:14px;color:var(--muted)}
.updates{display:inline-flex;align-items:center;gap:8px;margin:18px auto 0;padding:8px 14px;border-radius:999px;background:#fff;border:1px solid var(--line);font-size:14px;color:var(--muted)}
.dot{width:9px;height:9px;border-radius:50%;background:#DC2626;flex:none}
section{padding:36px 0 0}
h2{font-size:22px;letter-spacing:-.01em;margin:0 0 14px;font-weight:800}
h3{font-size:17px;margin:0 0 2px;font-weight:700}
p{margin:0}
ul.cards{list-style:none;margin:0;padding:0;display:grid;gap:12px}
@media (min-width:640px){ul.cards{grid-template-columns:1fr 1fr}}
.card{display:flex;gap:14px;align-items:flex-start;background:var(--card);border-radius:18px;padding:16px;box-shadow:0 1px 3px rgba(17,24,39,.06)}
.card p,.steps p{font-size:15px;color:var(--muted)}
.chip{width:44px;height:44px;border-radius:13px;display:flex;align-items:center;justify-content:center;flex:none}
ol.steps{list-style:none;margin:0;padding:0;background:var(--card);border-radius:18px;box-shadow:0 1px 3px rgba(17,24,39,.06)}
.steps li{display:flex;gap:14px;padding:16px;border-bottom:1px solid #F1F2F4}
.steps li:last-child{border-bottom:0}
.num{width:32px;height:32px;border-radius:50%;background:var(--blue);color:#fff;font-weight:800;display:flex;align-items:center;justify-content:center;flex:none}
.note{background:#FEF3C7;color:#78350F;border-radius:18px;padding:16px 18px;font-size:15px}
.note h3{color:#78350F}
footer{padding:40px 0 calc(32px + env(safe-area-inset-bottom));text-align:center;font-size:14px;color:var(--muted)}
footer a{display:inline-flex;align-items:center;gap:6px;color:var(--muted);text-decoration:none;font-weight:600}
footer a:hover{color:var(--ink)}
footer .by{margin-top:8px}
@media (prefers-reduced-motion:reduce){.btn{transition:none}}
</style>
</head>
<body>
<div class="bg" aria-hidden="true"></div>
<div class="wrap">
<header class="top">
  <a class="brand" href="/"><img src="/icon.svg" alt="" width="32" height="32">Cozy MDM Check</a>
  <a class="lang" href="/?lang=${other}" hreflang="${other}">${t.other}</a>
</header>
<main>
  <div class="hero">
    <img class="icon" src="/icon.svg" alt="Cozy MDM Check" width="96" height="96">
    <h1>Cozy MDM Check</h1>
    <p class="tag">${t.tagline}</p>
    ${button}
    <p class="meta">${meta}</p>
    <p class="updates"><span class="dot"></span>${t.updates}</p>
  </div>
  <section aria-labelledby="what"><h2 id="what">${t.whatTitle}</h2><ul class="cards">${features}</ul></section>
  <section aria-labelledby="how"><h2 id="how">${t.stepsTitle}</h2><ol class="steps">${steps}</ol></section>
  <section><div class="note"><h3>${t.noteTitle}</h3><p>${t.note}</p></div></section>
</main>
<footer>
  <a href="https://github.com/${REPO}">${svg("github", 18)}${t.source}</a>
  <p class="by">Cozy Pi</p>
</footer>
</div>
</body>
</html>`;
}
