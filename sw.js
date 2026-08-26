const CACHE_NAME = "lifestyle-shell-v3";
const SHARE_CACHE = "lifestyle-shared";
const ASSETS = [
  "./",
  "./index.html",
  "./manifest.json",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-maskable-512.png"
];

self.addEventListener("install", (e) => {
  self.skipWaiting();
  e.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// A PDF shared into the app from another app lands here. Stash it, then
// redirect into the UI, which picks it up and runs the statement parser.
self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method === "POST" && url.pathname.endsWith("/share-target")) {
    e.respondWith((async () => {
      try {
        const form = await e.request.formData();
        const text = (form.get("text") || "") + "";
        if (text.trim()) {
          const cache = await caches.open(SHARE_CACHE);
          await cache.put("/__shared_text", new Response(text, { headers: { "Content-Type": "text/plain" } }));
          return Response.redirect("./?shared=text", 303);
        }
        const file = form.get("statement");
        if (file && file.size) {
          const cache = await caches.open(SHARE_CACHE);
          await cache.put(
            "/__shared_statement",
            new Response(file, {
              headers: {
                "Content-Type": file.type || "application/pdf",
                "X-Filename": encodeURIComponent(file.name || "statement.pdf")
              }
            })
          );
          return Response.redirect("./?shared=statement", 303);
        }
      } catch (err) {}
      return Response.redirect("./", 303);
    })());
    return;
  }
  if (e.request.method !== "GET") return;
  if (new URL(e.request.url).origin !== self.location.origin) return;
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const resClone = res.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(e.request, resClone));
        return res;
      })
      .catch(() => caches.match(e.request).then((cached) => cached || caches.match("./index.html")))
  );
});
