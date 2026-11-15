/* In-game window translations supplied locally by the active language pack. ES5 for Awesomium. */
(function () {
    'use strict';
    var dictionary = {};
    if (typeof window.AetherfallWebText === 'string') {
        try {
            var parsed = JSON.parse(window.AetherfallWebText);
            if (parsed && Object.prototype.toString.call(parsed) === '[object Object]') dictionary = parsed;
        } catch (error) { /* Missing or invalid pack data leaves the original wording. */ }
        delete window.AetherfallWebText;
    }

    function translate(text) {
        if (Object.prototype.hasOwnProperty.call(dictionary, text) && typeof dictionary[text] === 'string') return dictionary[text];
        return text;
    }

    function format(text, fields) {
        return translate(text).replace(/\{([a-zA-Z0-9_]+)\}/g, function (match, key) {
            return Object.prototype.hasOwnProperty.call(fields, key) ? fields[key] : match;
        });
    }

    function translateStatic(node) {
        if (node.nodeType === 3) {
            var source = node.nodeValue, key = source.replace(/^\s+|\s+$/g, ''), text = translate(key);
            if (key !== text) {
                var offset = source.indexOf(key);
                node.nodeValue = source.slice(0, offset) + text + source.slice(offset + key.length);
            }
            return;
        }
        if (node.nodeType !== 1 || /^(SCRIPT|STYLE|NOSCRIPT)$/.test(node.tagName) || node.hasAttribute('data-af-raw')) return;
        var attributes = ['title', 'alt', 'aria-label', 'placeholder'], i, child;
        for (i = 0; i < attributes.length; i++) {
            var original = node.getAttribute(attributes[i]);
            if (original && original !== translate(original)) node.setAttribute(attributes[i], translate(original));
        }
        if (node.tagName === 'OPTION' && !node.hasAttribute('value')) node.setAttribute('value', node.value);
        for (child = node.firstChild; child; child = child.nextSibling) translateStatic(child);
    }

    var available = Object.keys(dictionary).length > 0;
    function translateShell(node) {
        if (available) translateStatic(node);
    }

    window.AetherfallWeb = { text: translate, format: format, shell: translateShell };
    // Only walk the authored shell, before the page starts rendering player data.
    translateShell(document.documentElement);
}());
