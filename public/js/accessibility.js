/**
 * Accessibility & Usability Improvements Module
 * Handles keyboard navigation, ARIA labels, focus management, and UX enhancements
 */

class AccessibilityManager {
    constructor() {
        this.focusableElements = [];
        this.currentFocusIndex = -1;
    }

    /**
     * Initialize accessibility features
     */
    init() {
        this.setupKeyboardNavigation();
        this.setupAriaLabels();
        this.setupFocusManagement();
        this.setupSkipLinks();
        this.setupLiveRegions();
    }

    /**
     * Setup keyboard navigation (Tab, Enter, Escape, Arrow keys)
     */
    setupKeyboardNavigation() {
        document.addEventListener('keydown', (e) => {
            // Escape key to close modals
            if (e.key === 'Escape') {
                const modals = document.querySelectorAll('[role="dialog"]:not(.hidden)');
                modals.forEach(modal => {
                    const closeBtn = modal.querySelector('[aria-label*="Fechar"], [aria-label*="Close"]');
                    if (closeBtn) closeBtn.click();
                });
            }

            // Enter key for buttons and links
            if (e.key === 'Enter' && (e.target.tagName === 'BUTTON' || e.target.tagName === 'A')) {
                e.target.click();
            }

            // Arrow keys for list navigation
            if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
                const listItems = document.querySelectorAll('[role="listitem"], .cat-select-btn, .media-item');
                if (listItems.length > 0) {
                    this.handleArrowKeyNavigation(e, listItems);
                }
            }
        });
    }

    /**
     * Handle arrow key navigation in lists
     */
    handleArrowKeyNavigation(e, items) {
        const currentIndex = Array.from(items).indexOf(document.activeElement);
        let nextIndex = currentIndex;

        if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
            nextIndex = (currentIndex + 1) % items.length;
            e.preventDefault();
        } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
            nextIndex = (currentIndex - 1 + items.length) % items.length;
            e.preventDefault();
        }

        items[nextIndex].focus();
    }

    /**
     * Add ARIA labels and roles for better screen reader support
     */
    setupAriaLabels() {
        // Add ARIA labels to interactive elements
        const elements = {
            '#login-button': { label: 'Acessar Portal IPTV', role: 'button' },
            '#logout-button': { label: 'Desconectar do portal', role: 'button' },
            '#toggle-password': { label: 'Mostrar/Ocultar senha', role: 'button' },
            '#search-input': { label: 'Buscar canais, filmes ou séries', role: 'searchbox' },
            '#mobile-category-trigger': { label: 'Abrir seletor de categorias', role: 'button' },
            '#pip-button': { label: 'Ativar Picture-in-Picture', role: 'button' },
            '.type-filter-btn': { label: 'Filtrar por tipo de conteúdo', role: 'button' },
            '.cat-select-btn': { label: 'Selecionar categoria', role: 'button' },
        };

        Object.entries(elements).forEach(([selector, { label, role }]) => {
            document.querySelectorAll(selector).forEach(el => {
                if (!el.getAttribute('aria-label')) {
                    el.setAttribute('aria-label', label);
                }
                if (role && !el.getAttribute('role')) {
                    el.setAttribute('role', role);
                }
            });
        });

        // Add ARIA live regions for dynamic content
        const liveRegions = {
            '#loader-message': 'polite',
            '#latency-status-indicator': 'polite',
            '#fav-count-badge': 'polite'
        };

        Object.entries(liveRegions).forEach(([selector, politeness]) => {
            const el = document.querySelector(selector);
            if (el) {
                el.setAttribute('aria-live', politeness);
                el.setAttribute('aria-atomic', 'true');
            }
        });
    }

    /**
     * Setup focus management and visible focus indicators
     */
    setupFocusManagement() {
        // Add visible focus indicators
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                document.body.classList.add('keyboard-nav');
            }
        });

        document.addEventListener('mousedown', () => {
            document.body.classList.remove('keyboard-nav');
        });

        // Trap focus in modals
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                const modal = document.querySelector('[role="dialog"]:not(.hidden)');
                if (modal) {
                    this.trapFocusInModal(e, modal);
                }
            }
        });
    }

    /**
     * Trap focus within modal dialogs
     */
    trapFocusInModal(e, modal) {
        const focusableElements = modal.querySelectorAll(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        
        if (focusableElements.length === 0) return;

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey) {
            if (document.activeElement === firstElement) {
                e.preventDefault();
                lastElement.focus();
            }
        } else {
            if (document.activeElement === lastElement) {
                e.preventDefault();
                firstElement.focus();
            }
        }
    }

    /**
     * Add skip links for keyboard navigation
     */
    setupSkipLinks() {
        const skipLink = document.createElement('a');
        skipLink.href = '#main-content';
        skipLink.className = 'skip-link';
        skipLink.textContent = 'Pular para conteúdo principal';
        skipLink.setAttribute('aria-label', 'Pular para conteúdo principal');
        document.body.insertBefore(skipLink, document.body.firstChild);

        // Add CSS for skip link
        const style = document.createElement('style');
        style.textContent = `
            .skip-link {
                position: absolute;
                top: -40px;
                left: 0;
                background: #E50914;
                color: white;
                padding: 8px;
                text-decoration: none;
                z-index: 100;
                border-radius: 0 0 4px 0;
            }
            .skip-link:focus {
                top: 0;
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Setup ARIA live regions for announcements
     */
    setupLiveRegions() {
        const liveRegion = document.createElement('div');
        liveRegion.id = 'aria-live-region';
        liveRegion.setAttribute('aria-live', 'polite');
        liveRegion.setAttribute('aria-atomic', 'true');
        liveRegion.className = 'sr-only';
        liveRegion.style.cssText = `
            position: absolute;
            left: -10000px;
            width: 1px;
            height: 1px;
            overflow: hidden;
        `;
        document.body.appendChild(liveRegion);
    }

    /**
     * Announce messages to screen readers
     */
    announce(message) {
        const region = document.getElementById('aria-live-region');
        if (region) {
            region.textContent = message;
            // Clear after announcement
            setTimeout(() => {
                region.textContent = '';
            }, 1000);
        }
    }

    /**
     * Setup color contrast checker
     */
    setupContrastChecker() {
        const style = document.createElement('style');
        style.textContent = `
            @media (prefers-color-scheme: dark) {
                body {
                    background-color: #0d0f14;
                    color: #f3f4f6;
                }
            }
            
            @media (prefers-color-scheme: light) {
                body {
                    background-color: #f5f5f5;
                    color: #1a1a1a;
                }
                .glass-card {
                    background: rgba(255, 255, 255, 0.9);
                }
            }
            
            @media (prefers-reduced-motion: reduce) {
                * {
                    animation-duration: 0.01ms !important;
                    animation-iteration-count: 1 !important;
                    transition-duration: 0.01ms !important;
                }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Setup text size adjustment
     */
    setupTextSizeAdjustment() {
        const style = document.createElement('style');
        style.textContent = `
            @media (max-width: 640px) {
                body { font-size: 16px; }
            }
            
            @media (min-width: 641px) and (max-width: 1024px) {
                body { font-size: 15px; }
            }
            
            @media (min-width: 1025px) {
                body { font-size: 14px; }
            }
        `;
        document.head.appendChild(style);
    }

    /**
     * Cleanup
     */
    destroy() {
        // Cleanup event listeners if needed
    }
}

// Export for use in main script
const accessibilityManager = new AccessibilityManager();
