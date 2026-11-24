// Service worker: lets the Home Screen app open offline and is what shows notifications on iOS.
//
// Network first, cache as fallback — so a new deploy is picked up on the next launch with a
// connection, instead of an old cached build sticking around until the cache name changes.
// Supabase calls are never cached: they are the sync, and stale data there would be wrong.
var CACHE = 'splits-shell-v1';

self.addEventListener('install', function () { self.skipWaiting(); });

self.addEventListener('activate', function (event) {
  event.waitUntil(self.clients.claim());
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

  event.respondWith(
    fetch(request)
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
