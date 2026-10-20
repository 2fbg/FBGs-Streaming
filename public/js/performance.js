/**
 * Performance Optimization Module
 * Handles caching, lazy loading, debouncing, and other performance improvements
 */

class PerformanceOptimizer {
    constructor() {
        this.cache = new Map();
        this.debounceTimers = new Map();
        this.requestCache = new Map();
        this.imageObserver = null;
        this.intersectionObserver = null;
    }

    /**
     * Debounce function to prevent excessive function calls
     */
    debounce(key, fn, delay = 300) {
        if (this.debounceTimers.has(key)) {
            clearTimeout(this.debounceTimers.get(key));
        }
        
        const timer = setTimeout(() => {
            fn();
            this.debounceTimers.delete(key);
        }, delay);
        
        this.debounceTimers.set(key, timer);
    }

    /**
     * Simple cache with TTL (Time To Live)
     */
    setCache(key, value, ttl = 3600000) { // Default 1 hour
        const expiry = Date.now() + ttl;
        this.cache.set(key, { value, expiry });
    }

    getCache(key) {
        const item = this.cache.get(key);
        if (!item) return null;
        
        if (Date.now() > item.expiry) {
            this.cache.delete(key);
            return null;
        }
        
        return item.value;
    }

    clearCache() {
        this.cache.clear();
    }

    /**
     * Lazy load images with Intersection Observer
     */
    initLazyLoading() {
        if (!('IntersectionObserver' in window)) {
            // Fallback for older browsers
            document.querySelectorAll('img[data-src]').forEach(img => {
                img.src = img.dataset.src;
            });
            return;
        }

        this.imageObserver = new IntersectionObserver((entries, observer) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    const img = entry.target;
                    if (img.dataset.src) {
                        img.src = img.dataset.src;
                        img.removeAttribute('data-src');
                        observer.unobserve(img);
                    }
                }
            });
        }, {
            rootMargin: '50px'
        });

        document.querySelectorAll('img[data-src]').forEach(img => {
            this.imageObserver.observe(img);
        });
    }

    /**
     * Observe elements for visibility and trigger callbacks
     */
    observeElements(selector, callback, options = {}) {
        if (!('IntersectionObserver' in window)) return;

        this.intersectionObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    callback(entry.target);
                }
            });
        }, {
            threshold: 0.1,
            ...options
        });

        document.querySelectorAll(selector).forEach(el => {
            this.intersectionObserver.observe(el);
        });
    }

    /**
     * Request deduplication - avoid duplicate API calls
     */
    async fetchWithCache(url, options = {}, cacheKey = null) {
        const key = cacheKey || url;
        
        // Check cache first
        const cached = this.getCache(key);
        if (cached) return cached;

        // Check if request is already in flight
        if (this.requestCache.has(key)) {
            return this.requestCache.get(key);
        }

        // Make the request
        const promise = fetch(url, {
            ...options,
            signal: options.signal || AbortSignal.timeout(10000)
        }).then(response => {
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            return response.text();
        }).then(data => {
            this.requestCache.delete(key);
            this.setCache(key, data, options.cacheTTL || 3600000);
            return data;
        }).catch(error => {
            this.requestCache.delete(key);
            throw error;
        });

        this.requestCache.set(key, promise);
        return promise;
    }

    /**
     * Throttle function for high-frequency events
     */
    throttle(fn, delay = 300) {
        let lastCall = 0;
        return function(...args) {
            const now = Date.now();
            if (now - lastCall >= delay) {
                lastCall = now;
                fn.apply(this, args);
            }
        };
    }

    /**
     * Batch DOM updates to avoid layout thrashing
     */
    batchDOMUpdates(updates) {
        requestAnimationFrame(() => {
            updates.forEach(update => update());
        });
    }

    /**
     * Virtual scrolling helper for large lists
     */
    createVirtualScroller(container, items, itemHeight, renderItem) {
        let scrollTop = 0;
        const visibleItems = Math.ceil(container.clientHeight / itemHeight);
        
        container.addEventListener('scroll', () => {
            scrollTop = container.scrollTop;
            this.renderVisibleItems();
        });

        const renderVisibleItems = () => {
            const startIndex = Math.floor(scrollTop / itemHeight);
            const endIndex = startIndex + visibleItems + 1;
            
            container.innerHTML = '';
            items.slice(startIndex, endIndex).forEach((item, index) => {
                const el = renderItem(item, startIndex + index);
                container.appendChild(el);
            });
        };

        renderVisibleItems();
    }

    /**
     * Cleanup resources
     */
    destroy() {
        if (this.imageObserver) this.imageObserver.disconnect();
        if (this.intersectionObserver) this.intersectionObserver.disconnect();
        this.debounceTimers.forEach(timer => clearTimeout(timer));
        this.cache.clear();
        this.requestCache.clear();
    }
}

// Export for use in main script
const performanceOptimizer = new PerformanceOptimizer();
