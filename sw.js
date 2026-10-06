// MK21 Web Service Worker for PWA shell caching and installation
const CACHE_NAME = 'mk21-web-cache-v1';
const STATIC_ASSETS = [
    './',
    './index.html',
    './manifest.json',
    './icon.svg',
    './css/styles.css',
    './css/responsive.css'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            return cache.addAll(STATIC_ASSETS).catch((err) => {
                console.warn('SW cache.addAll partially failed', err);
            });
        })
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) => {
            return Promise.all(
                keys.map((key) => {
                    if (key !== CACHE_NAME) {
                        return caches.delete(key);
                    }
                })
            );
        })
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);

    // Never intercept API requests, proxies, or live video streams
    if (url.pathname.startsWith('/api/') || 
        url.pathname.includes('/proxy') || 
        url.search.includes('url=') ||
        event.request.headers.get('range')) {
        return;
    }

    // Network-first for dynamic files, fallback to cache
    event.respondWith(
        fetch(event.request)
            .then((response) => {
                if (response && response.status === 200 && response.type === 'basic') {
                    const responseClone = response.clone();
                    caches.open(CACHE_NAME).then((cache) => {
                        cache.put(event.request, responseClone);
                    });
                }
                return response;
            })
            .catch(() => caches.match(event.request))
    );
});
