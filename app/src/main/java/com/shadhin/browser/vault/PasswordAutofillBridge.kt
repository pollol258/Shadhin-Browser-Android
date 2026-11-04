package com.shadhin.browser.vault

import android.webkit.JavascriptInterface

/**
 * JavaScript interface bridging WebView DOM password forms and the Android Password Manager.
 */
class PasswordAutofillBridge(
    private val onLoginFormDetected: (origin: String) -> Unit,
    private val onPasswordSubmitted: (origin: String, username: String, password: String) -> Unit
) {

    @JavascriptInterface
    fun notifyLoginFormDetected(origin: String) {
        onLoginFormDetected(origin)
    }

    @JavascriptInterface
    fun notifyPasswordSubmitted(origin: String, username: String, password: String) {
        if (username.isNotBlank() && password.isNotBlank()) {
            onPasswordSubmitted(origin, username, password)
        }
    }

    companion object {
        const val INTERFACE_NAME = "__shadhin_autofill__"

        /**
         * Lightweight DOM observer script to detect password forms, observe focus,
         * and capture form submissions for password saving.
         */
        val INJECTION_SCRIPT = """
            (function() {
                if (window.__shadhin_autofill_installed__) return;
                window.__shadhin_autofill_installed__ = true;

                function checkAndAttach() {
                    var passInputs = document.querySelectorAll('input[type="password"]');
                    if (!passInputs || passInputs.length === 0) return;

                    // Notify login form detected on page or when focused
                    passInputs.forEach(function(input) {
                        if (input.__shadhin_bound__) return;
                        input.__shadhin_bound__ = true;

                        input.addEventListener('focus', function() {
                            if (window.$INTERFACE_NAME) {
                                window.$INTERFACE_NAME.notifyLoginFormDetected(window.location.hostname);
                            }
                        });
                        input.addEventListener('click', function() {
                            if (window.$INTERFACE_NAME) {
                                window.$INTERFACE_NAME.notifyLoginFormDetected(window.location.hostname);
                            }
                        });
                    });

                    // Intercept form submissions
                    var forms = document.querySelectorAll('form');
                    forms.forEach(function(form) {
                        if (form.__shadhin_submit_bound__) return;
                        form.__shadhin_submit_bound__ = true;

                        form.addEventListener('submit', function() {
                            var pInput = form.querySelector('input[type="password"]');
                            if (!pInput || !pInput.value) return;

                            var uInput = form.querySelector('input[type="text"], input[type="email"], input[autocomplete="username"], input[name*="user"], input[name*="login"], input[name*="email"]');
                            var usernameVal = uInput ? uInput.value : '';
                            var passwordVal = pInput.value;

                            if (usernameVal && passwordVal && window.$INTERFACE_NAME) {
                                window.$INTERFACE_NAME.notifyPasswordSubmitted(window.location.hostname, usernameVal, passwordVal);
                            }
                        });
                    });
                }

                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', checkAndAttach);
                } else {
                    checkAndAttach();
                }

                // Also check on DOM subtree changes (for SPAs like React/Vue)
                var observer = new MutationObserver(function() {
                    checkAndAttach();
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            })();
        """.trimIndent()

        /**
         * Generates the JavaScript call to auto-fill the detected login fields.
         */
        fun buildAutofillScript(username: String, password: String): String {
            val safeUser = escapeJsString(username)
            val safePass = escapeJsString(password)
            return """
                (function() {
                    var passInputs = document.querySelectorAll('input[type="password"]');
                    if (!passInputs || passInputs.length === 0) return false;
                    var passInput = passInputs[0];
                    var form = passInput.form || document;
                    var userInputs = form.querySelectorAll('input[type="text"], input[type="email"], input[autocomplete="username"], input[name*="user"], input[name*="login"], input[name*="email"]');
                    var userInput = userInputs.length > 0 ? userInputs[0] : null;

                    if (userInput) {
                        userInput.focus();
                        userInput.value = '$safeUser';
                        userInput.dispatchEvent(new Event('input', { bubbles: true }));
                        userInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    if (passInput) {
                        passInput.focus();
                        passInput.value = '$safePass';
                        passInput.dispatchEvent(new Event('input', { bubbles: true }));
                        passInput.dispatchEvent(new Event('change', { bubbles: true }));
                        passInput.blur();
                    }
                    return true;
                })();
            """.trimIndent()
        }

        private fun escapeJsString(raw: String): String {
            return raw.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
        }
    }
}
