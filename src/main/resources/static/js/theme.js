/**
 * GharFix Theme Engine
 * =====================
 * Applies the saved theme immediately on load (before CSS renders) to
 * prevent any light-to-dark flash. Also provides toggle UI controls.
 *
 * Usage:
 *   GFTheme.set('dark')   — switch to dark
 *   GFTheme.set('light')  — switch to light
 *   GFTheme.get()         — returns current theme string
 */
(function () {
    'use strict';

    var STORAGE_KEY = 'gf-theme';
    var DEFAULT_THEME = 'light';

    /** Read from localStorage, fall back to default */
    function getSavedTheme() {
        try {
            return localStorage.getItem(STORAGE_KEY) || DEFAULT_THEME;
        } catch (e) {
            return DEFAULT_THEME;
        }
    }

    /** Apply data-theme attribute to <html> immediately */
    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
    }

    /** Update all .ud-theme-btn buttons to show active state */
    function syncButtons(theme) {
        var lightBtns = document.querySelectorAll('[data-gf-theme-btn="light"]');
        var darkBtns  = document.querySelectorAll('[data-gf-theme-btn="dark"]');
        lightBtns.forEach(function (btn) {
            btn.classList.toggle('active', theme === 'light');
            btn.setAttribute('aria-pressed', String(theme === 'light'));
        });
        darkBtns.forEach(function (btn) {
            btn.classList.toggle('active', theme === 'dark');
            btn.setAttribute('aria-pressed', String(theme === 'dark'));
        });
    }

    /** Public API */
    window.GFTheme = {
        /** Get the current active theme */
        get: function () {
            return document.documentElement.getAttribute('data-theme') || DEFAULT_THEME;
        },

        /** Set and persist a theme */
        set: function (theme) {
            if (theme !== 'light' && theme !== 'dark') return;

            /* Add transition class for smooth animation */
            document.documentElement.classList.add('gf-theme-transitioning');

            applyTheme(theme);
            syncButtons(theme);

            try {
                localStorage.setItem(STORAGE_KEY, theme);
            } catch (e) { /* storage blocked */ }

            /* Remove transition class after animation */
            setTimeout(function () {
                document.documentElement.classList.remove('gf-theme-transitioning');
            }, 400);
        },

        /** Toggle between light and dark */
        toggle: function () {
            var current = window.GFTheme.get();
            window.GFTheme.set(current === 'dark' ? 'light' : 'dark');
        },

        /** Call after DOM ready to sync button states */
        init: function () {
            var theme = getSavedTheme();
            applyTheme(theme);
            syncButtons(theme);
        }
    };

    /* -- EARLY APPLY (runs synchronously, before any paint) -- */
    applyTheme(getSavedTheme());

    /* -- SYNC BUTTONS after DOM is ready -- */
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            GFTheme.init();
        });
    } else {
        GFTheme.init();
    }
}());
