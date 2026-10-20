/**
 * SEO and Meta Tags Optimization Module
 * Handles dynamic meta tags, Open Graph, and SEO improvements
 */

class SEOManager {
    constructor() {
        this.defaultMeta = {
            title: 'MK21 MultiServidor Web - Portal IPTV de Alto Desempenho',
            description: 'Portal IPTV web de alto desempenho com suporte a múltiplos servidores, streaming de canais ao vivo, filmes e séries com interface moderna e responsiva.',
            keywords: 'IPTV, streaming, canais ao vivo, filmes, séries, portal, web player',
            author: 'MK21 MultiServidor',
            ogImage: 'https://via.placeholder.com/1200x630?text=MK21+IPTV+Portal',
            ogUrl: window.location.href,
            ogType: 'website',
            twitterCard: 'summary_large_image'
        };
    }

    /**
     * Initialize SEO optimizations
     */
    init() {
        this.setupBasicMeta();
        this.setupOpenGraph();
        this.setupTwitterCard();
        this.setupStructuredData();
        this.setupRobotsMeta();
        this.setupViewportMeta();
    }

    /**
     * Setup basic meta tags
     */
    setupBasicMeta() {
        this.setMeta('name', 'description', this.defaultMeta.description);
        this.setMeta('name', 'keywords', this.defaultMeta.keywords);
        this.setMeta('name', 'author', this.defaultMeta.author);
        this.setMeta('name', 'theme-color', '#E50914');
        this.setMeta('name', 'msapplication-TileColor', '#E50914');
        
        // Language
        document.documentElement.lang = 'pt-BR';
    }

    /**
     * Setup Open Graph meta tags
     */
    setupOpenGraph() {
        this.setMeta('property', 'og:title', this.defaultMeta.title);
        this.setMeta('property', 'og:description', this.defaultMeta.description);
        this.setMeta('property', 'og:image', this.defaultMeta.ogImage);
        this.setMeta('property', 'og:url', this.defaultMeta.ogUrl);
        this.setMeta('property', 'og:type', this.defaultMeta.ogType);
        this.setMeta('property', 'og:locale', 'pt_BR');
    }

    /**
     * Setup Twitter Card meta tags
     */
    setupTwitterCard() {
        this.setMeta('name', 'twitter:card', this.defaultMeta.twitterCard);
        this.setMeta('name', 'twitter:title', this.defaultMeta.title);
        this.setMeta('name', 'twitter:description', this.defaultMeta.description);
        this.setMeta('name', 'twitter:image', this.defaultMeta.ogImage);
    }

    /**
     * Setup structured data (JSON-LD)
     */
    setupStructuredData() {
        const structuredData = {
            '@context': 'https://schema.org',
            '@type': 'WebApplication',
            'name': 'MK21 IPTV Portal',
            'description': this.defaultMeta.description,
            'url': window.location.href,
            'applicationCategory': 'MultimediaApplication',
            'offers': {
                '@type': 'Offer',
                'price': '0',
                'priceCurrency': 'BRL'
            },
            'author': {
                '@type': 'Organization',
                'name': 'MK21 MultiServidor'
            }
        };

        const script = document.createElement('script');
        script.type = 'application/ld+json';
        script.textContent = JSON.stringify(structuredData);
        document.head.appendChild(script);
    }

    /**
     * Setup robots meta tag
     */
    setupRobotsMeta() {
        this.setMeta('name', 'robots', 'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1');
    }

    /**
     * Setup viewport meta tag for mobile optimization
     */
    setupViewportMeta() {
        this.setMeta('name', 'viewport', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
    }

    /**
     * Helper to set or update meta tags
     */
    setMeta(type, name, content) {
        let meta = document.querySelector(`meta[${type}="${name}"]`);
        
        if (!meta) {
            meta = document.createElement('meta');
            meta.setAttribute(type, name);
            document.head.appendChild(meta);
        }
        
        meta.setAttribute('content', content);
    }

    /**
     * Update meta tags dynamically when content changes
     */
    updateMetaForContent(item) {
        if (!item) return;

        const title = `${item.name} - MK21 IPTV Portal`;
        const description = `Assistir ${item.name} em ${item.category} - Portal IPTV MK21`;

        document.title = title;
        this.setMeta('property', 'og:title', title);
        this.setMeta('property', 'og:description', description);
        this.setMeta('name', 'description', description);

        if (item.logo) {
            this.setMeta('property', 'og:image', item.logo);
            this.setMeta('name', 'twitter:image', item.logo);
        }
    }

    /**
     * Setup canonical URL
     */
    setupCanonicalUrl() {
        let canonical = document.querySelector('link[rel="canonical"]');
        if (!canonical) {
            canonical = document.createElement('link');
            canonical.rel = 'canonical';
            document.head.appendChild(canonical);
        }
        canonical.href = window.location.href;
    }

    /**
     * Setup alternate language links
     */
    setupAlternateLanguages() {
        const languages = [
            { hreflang: 'pt-BR', href: window.location.href },
            { hreflang: 'pt', href: window.location.href },
            { hreflang: 'x-default', href: window.location.href }
        ];

        languages.forEach(lang => {
            let link = document.querySelector(`link[hreflang="${lang.hreflang}"]`);
            if (!link) {
                link = document.createElement('link');
                link.rel = 'alternate';
                link.hreflang = lang.hreflang;
                document.head.appendChild(link);
            }
            link.href = lang.href;
        });
    }

    /**
     * Setup preload and prefetch hints for performance
     */
    setupResourceHints() {
        // Preload critical resources
        const preloads = [
            { href: 'https://cdn.tailwindcss.com', as: 'script' },
            { href: 'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css', as: 'style' }
        ];

        preloads.forEach(preload => {
            const link = document.createElement('link');
            link.rel = 'preload';
            link.href = preload.href;
            link.as = preload.as;
            document.head.appendChild(link);
        });

        // Prefetch DNS for external resources
        const dnsPrefetch = [
            'https://cdn.tailwindcss.com',
            'https://cdnjs.cloudflare.com',
            'https://cdn.jsdelivr.net',
            'https://fonts.googleapis.com'
        ];

        dnsPrefetch.forEach(url => {
            const link = document.createElement('link');
            link.rel = 'dns-prefetch';
            link.href = url;
            document.head.appendChild(link);
        });
    }

    /**
     * Setup manifest file for PWA
     */
    setupManifest() {
        const manifest = {
            name: 'MK21 IPTV Portal',
            short_name: 'MK21 IPTV',
            description: this.defaultMeta.description,
            start_url: '/',
            display: 'standalone',
            background_color: '#0d0f14',
            theme_color: '#E50914',
            icons: [
                {
                    src: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192"><rect fill="%23E50914" width="192" height="192"/><text x="50%" y="50%" font-size="120" fill="white" text-anchor="middle" dominant-baseline="central" font-weight="bold">MK</text></svg>',
                    sizes: '192x192',
                    type: 'image/svg+xml'
                }
            ]
        };

        let link = document.querySelector('link[rel="manifest"]');
        if (!link) {
            link = document.createElement('link');
            link.rel = 'manifest';
            document.head.appendChild(link);
        }
        link.href = 'data:application/manifest+json,' + encodeURIComponent(JSON.stringify(manifest));
    }
}

// Export for use in main script
const seoManager = new SEOManager();
