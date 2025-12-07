/**
 * Sparkle JavaScript Utilities
 * BTN Instance - Unified JS Framework
 * 
 * Namespace: SPK
 */

(function(window, document) {
    'use strict';

    // Create namespace
    const SPK = window.SPK || {};

    /**
     * Initialize all Sparkle components
     */
    SPK.init = function() {
        SPK.initNavbar();
        SPK.initTooltips();
        SPK.initCopyButtons();
        SPK.initConfirmDialogs();
    };

    /**
     * Mobile Navbar Toggle
     */
    SPK.initNavbar = function() {
        const toggle = document.querySelector('.spk-navbar__toggle');
        const menu = document.querySelector('.spk-navbar__menu');
        
        if (toggle && menu) {
            toggle.addEventListener('click', function(e) {
                e.preventDefault();
                menu.classList.toggle('spk-navbar__menu--open');
                
                // Update toggle icon
                const icon = toggle.querySelector('i');
                if (icon) {
                    if (menu.classList.contains('spk-navbar__menu--open')) {
                        icon.className = 'fas fa-times';
                    } else {
                        icon.className = 'fas fa-bars';
                    }
                }
            });

            // Close menu when clicking outside
            document.addEventListener('click', function(e) {
                if (!toggle.contains(e.target) && !menu.contains(e.target)) {
                    menu.classList.remove('spk-navbar__menu--open');
                    const icon = toggle.querySelector('i');
                    if (icon) {
                        icon.className = 'fas fa-bars';
                    }
                }
            });
        }
    };

    /**
     * Initialize Bootstrap Tooltips
     */
    SPK.initTooltips = function() {
        if (typeof $ !== 'undefined' && typeof $.fn.tooltip !== 'undefined') {
            $('[data-toggle="tooltip"]').tooltip();
        }
    };

    /**
     * Copy to Clipboard Utility
     */
    SPK.copyToClipboard = function(text, button) {
        if (!navigator.clipboard) {
            // Fallback for older browsers
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            document.body.appendChild(textarea);
            textarea.select();
            
            try {
                document.execCommand('copy');
                SPK.showCopySuccess(button);
            } catch (err) {
                console.error('Failed to copy:', err);
                alert('复制失败，请手动复制');
            }
            
            document.body.removeChild(textarea);
            return;
        }

        navigator.clipboard.writeText(text).then(function() {
            SPK.showCopySuccess(button);
        }).catch(function(err) {
            console.error('Failed to copy:', err);
            alert('复制失败，请手动复制');
        });
    };

    /**
     * Show copy success feedback
     */
    SPK.showCopySuccess = function(button) {
        if (!button) return;

        const originalHTML = button.innerHTML;
        button.innerHTML = '<i class="fas fa-check"></i> 已复制';
        button.classList.add('spk-credential__copy--copied');
        button.disabled = true;

        setTimeout(function() {
            button.innerHTML = originalHTML;
            button.classList.remove('spk-credential__copy--copied');
            button.disabled = false;
        }, 2000);
    };

    /**
     * Initialize Copy Buttons
     */
    SPK.initCopyButtons = function() {
        const copyButtons = document.querySelectorAll('[data-copy]');
        
        copyButtons.forEach(function(button) {
            button.addEventListener('click', function(e) {
                e.preventDefault();
                const targetSelector = button.getAttribute('data-copy');
                const targetElement = document.querySelector(targetSelector);
                
                if (targetElement) {
                    SPK.copyToClipboard(targetElement.textContent.trim(), button);
                }
            });
        });

        // Handle direct copy buttons with data-copy-text
        const copyTextButtons = document.querySelectorAll('[data-copy-text]');
        copyTextButtons.forEach(function(button) {
            button.addEventListener('click', function(e) {
                e.preventDefault();
                const text = button.getAttribute('data-copy-text');
                SPK.copyToClipboard(text, button);
            });
        });
    };

    /**
     * Initialize Confirm Dialogs
     */
    SPK.initConfirmDialogs = function() {
        const confirmLinks = document.querySelectorAll('[data-confirm]');
        
        confirmLinks.forEach(function(link) {
            link.addEventListener('click', function(e) {
                const message = link.getAttribute('data-confirm');
                if (!confirm(message)) {
                    e.preventDefault();
                }
            });
        });
    };

    /**
     * Format bytes to human readable string
     */
    SPK.formatBytes = function(bytes, decimals) {
        decimals = decimals || 2;
        if (bytes === 0) return '0 Bytes';
        
        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
        
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    };

    /**
     * Format date to locale string
     */
    SPK.formatDate = function(date, options) {
        options = options || {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        };
        
        return new Date(date).toLocaleString('zh-CN', options);
    };

    /**
     * Debounce utility
     */
    SPK.debounce = function(func, wait) {
        let timeout;
        return function executedFunction() {
            const context = this;
            const args = arguments;
            
            const later = function() {
                timeout = null;
                func.apply(context, args);
            };
            
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    };

    /**
     * Theme toggle (for manual theme switching)
     */
    SPK.toggleTheme = function() {
        const html = document.documentElement;
        const currentTheme = html.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        
        html.setAttribute('data-theme', newTheme);
        localStorage.setItem('spk-theme', newTheme);
    };

    /**
     * Initialize theme from localStorage
     */
    SPK.initTheme = function() {
        const savedTheme = localStorage.getItem('spk-theme');
        if (savedTheme) {
            document.documentElement.setAttribute('data-theme', savedTheme);
        }
    };

    // Export to global
    window.SPK = SPK;

    // Auto-initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', SPK.init);
    } else {
        SPK.init();
    }

})(window, document);
