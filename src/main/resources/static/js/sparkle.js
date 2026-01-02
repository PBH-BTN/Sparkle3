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
        SPK.initTrafficInfoBar();
        SPK.initTooltips();
        SPK.initCopyButtons();
        SPK.initConfirmDialogs();
    };

    /**
     * Adjust body padding based on traffic info bar presence
     */
    SPK.initTrafficInfoBar = function() {
        const trafficInfoBar = document.querySelector('.spk-traffic-info');
        const body = document.body;

        if (trafficInfoBar && body) {
            // Get the height of navbar and traffic info bar
            const navbarHeight = getComputedStyle(document.documentElement)
                .getPropertyValue('--spk-navbar-height') || '3.75rem';
            const trafficInfoHeight = trafficInfoBar.offsetHeight;

            // Calculate total top padding
            const navbarPx = parseFloat(navbarHeight) * 16; // Convert rem to px (assuming 16px base)
            const totalPadding = navbarPx + trafficInfoHeight;

            // Apply padding to body
            body.style.paddingTop = totalPadding + 'px';
        }
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

    /**
     * Render Pagination HTML
     * @param {Object} options - Pagination options
     * @param {number} options.currentPage - Current page number (1-based)
     * @param {number} options.totalPages - Total number of pages (optional, 0 means unknown)
     * @param {number} options.totalRecords - Total number of records (optional)
     * @param {number} options.pageSize - Records per page (optional)
     * @param {number} options.currentRecordCount - Number of records on current page (optional)
     * @param {Function} options.onPageChange - Callback function when page changes
     * @returns {string} HTML string for pagination
     */
    SPK.renderPagination = function(options) {
        const {
            currentPage = 1,
            totalPages = 1,
            totalRecords = null,
            pageSize = 100,
            currentRecordCount = 0,
            onPageChange = null
        } = options;

        // If totalPages is unknown (0 or null) but we have records, use simplified pagination
        const isUnknownTotal = (!totalPages || totalPages === 0);

        let html = '<div class="spk-pagination">';

        // Pagination info
        html += '<div class="spk-pagination__info">';
        if (totalRecords !== null && totalRecords > 0) {
            const startRecord = (currentPage - 1) * pageSize + 1;
            const endRecord = Math.min(currentPage * pageSize, totalRecords);
            html += `显示第 <strong>${startRecord.toLocaleString()}</strong> - <strong>${endRecord.toLocaleString()}</strong> 条，共 <strong>${totalRecords.toLocaleString()}</strong> 条记录`;
        } else if (isUnknownTotal) {
            html += `第 <strong>${currentPage}</strong> 页`;
        } else {
            html += `第 <strong>${currentPage}</strong> / <strong>${totalPages}</strong> 页`;
        }
        html += '</div>';

        // Pagination controls
        html += '<div class="spk-pagination__controls">';

        if (isUnknownTotal) {
            // Simplified pagination for unknown total
            // Previous page button
            if (currentPage > 1) {
                html += `<button class="spk-pagination__btn" data-page="${currentPage - 1}" title="上一页"><i class="fas fa-angle-left"></i> 上一页</button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="上一页"><i class="fas fa-angle-left"></i> 上一页</span>`;
            }

            // Current page indicator
            html += `<span class="spk-pagination__btn spk-pagination__btn--number spk-pagination__btn--active"><strong>${currentPage}</strong></span>`;

            // Next page button (enabled if current page has records matching page size)
            const hasNextPage = currentRecordCount >= pageSize;
            if (hasNextPage) {
                html += `<button class="spk-pagination__btn" data-page="${currentPage + 1}" title="下一页">下一页 <i class="fas fa-angle-right"></i></button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="下一页">下一页 <i class="fas fa-angle-right"></i></span>`;
            }
        } else {
            // Full pagination with known total
            // First page button
            if (currentPage > 1) {
                html += `<button class="spk-pagination__btn" data-page="1" title="首页"><i class="fas fa-angle-double-left"></i></button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="首页"><i class="fas fa-angle-double-left"></i></span>`;
            }

            // Previous page button
            if (currentPage > 1) {
                html += `<button class="spk-pagination__btn" data-page="${currentPage - 1}" title="上一页"><i class="fas fa-angle-left"></i> 上一页</button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="上一页"><i class="fas fa-angle-left"></i> 上一页</span>`;
            }

            // Page numbers
            const maxButtons = 7;
            const halfButtons = Math.floor(maxButtons / 2);
            let startPage = Math.max(1, currentPage - halfButtons);
            let endPage = Math.min(totalPages, startPage + maxButtons - 1);

            if (endPage - startPage < maxButtons - 1) {
                startPage = Math.max(1, endPage - maxButtons + 1);
            }

            // First page if not in range
            if (startPage > 1) {
                html += `<button class="spk-pagination__btn spk-pagination__btn--number" data-page="1">1</button>`;
                if (startPage > 2) {
                    html += '<span class="spk-pagination__ellipsis">...</span>';
                }
            }

            // Page number buttons
            for (let i = startPage; i <= endPage; i++) {
                if (i === currentPage) {
                    html += `<span class="spk-pagination__btn spk-pagination__btn--number spk-pagination__btn--active"><strong>${i}</strong></span>`;
                } else {
                    html += `<button class="spk-pagination__btn spk-pagination__btn--number" data-page="${i}">${i}</button>`;
                }
            }

            // Last page if not in range
            if (endPage < totalPages) {
                if (endPage < totalPages - 1) {
                    html += '<span class="spk-pagination__ellipsis">...</span>';
                }
                html += `<button class="spk-pagination__btn spk-pagination__btn--number" data-page="${totalPages}">${totalPages}</button>`;
            }

            // Next page button
            if (currentPage < totalPages) {
                html += `<button class="spk-pagination__btn" data-page="${currentPage + 1}" title="下一页">下一页 <i class="fas fa-angle-right"></i></button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="下一页">下一页 <i class="fas fa-angle-right"></i></span>`;
            }

            // Last page button
            if (currentPage < totalPages) {
                html += `<button class="spk-pagination__btn" data-page="${totalPages}" title="末页"><i class="fas fa-angle-double-right"></i></button>`;
            } else {
                html += `<span class="spk-pagination__btn spk-pagination__btn--disabled" title="末页"><i class="fas fa-angle-double-right"></i></span>`;
            }
        }

        html += '</div></div>';

        // Add event listeners if callback provided
        if (onPageChange && typeof onPageChange === 'function') {
            setTimeout(function() {
                const buttons = document.querySelectorAll('.spk-pagination__btn[data-page]');
                buttons.forEach(function(button) {
                    button.addEventListener('click', function() {
                        const page = parseInt(button.getAttribute('data-page'), 10);
                        onPageChange(page);
                    });
                });
            }, 0);
        }

        return html;
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


const formatTime = (time) => {
    if (!time || time === 0) return 'N/A';
    return new Date(time).toLocaleString();
};