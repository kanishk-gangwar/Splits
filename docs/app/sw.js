// Service worker: lets the Home Screen app open offline and is what shows notifications on iOS.
//
// Network first, cache as fallback — so a new deploy is picked up on the next launch with a
// connection, instead of an old cached build sticking around until the cache name changes.
// Supabase calls are never cached: they are the sync, and stale data there would be wrong.
// v2: sql.js is now loaded with CORS, and v1 holds it as an opaque no-CORS response, which a
// CORS request refuses — so v1 has to go.
var CACHE = 'splits-shell-v2';

self.addEventListener('install', function () { self.skipWaiting(); });

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys()
      .then(function (keys) {
        return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
      })
      .then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  var request = event.request;
  if (request.method !== 'GET') return;
  var url = new URL(request.url);

  // CDN files are pinned to a version in their URL, so they never change: cache first. The
  // emoji font alone is 10 MB, which should be downloaded once, not on every launch.
  if (url.hostname === 'cdnjs.cloudflare.com' || url.hostname === 'cdn.jsdelivr.net') {
    event.respondWith(
      caches.match(request).then(function (cached) {
        return cached || fetch(request).then(function (response) {
          if (response.ok) {
            var copy = response.clone();
            caches.open(CACHE).then(function (cache) { cache.put(request, copy); });
          }
          return response;
        });
      })
    );
    return;
  }

  if (url.origin !== self.location.origin) return;

  // 'no-cache' makes the browser check with the server instead of trusting its HTTP cache.
  // GitHub Pages lets files be cached for 10 minutes, and splits.js keeps its name across
  // deploys while the .wasm files it loads do not: a cached old splits.js asks for wasm that
  // a deploy has deleted, and the app hangs on the splash. Unchanged files cost a 304.
  event.respondWith(
    fetch(request, { cache: 'no-cache' })
      .then(function (response) {
        if (response.ok) {
          var copy = response.clone();
          caches.open(CACHE).then(function (cache) { cache.put(request, copy); });
        }
        return response;
      })
      .catch(function () {
        return caches.match(request).then(function (cached) {
          return cached || Response.error();
        });
      })
  );
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window' }).then(function (windows) {
      if (windows.length) return windows[0].focus();
      return self.clients.openWindow('./');
    })
  );
});
