/* Aion 4.8 browser: ES5, native item windows and asynchronous shop navigation. */
(function () {
  'use strict';
  var request = null, serial = 0, writing = false, timer = null;
  function message(text, error) {
    var main = document.querySelector('.main-content');
    if (!main) return;
    var node = main.querySelector('.shop-status');
    if (!node) { node = document.createElement('div'); main.insertBefore(node, main.firstChild); }
    node.className = 'shop-status' + (error ? ' is-error' : '');
    node.setAttribute('role', 'status'); node.textContent = text;
  }
  function busy(value) {
    var shell = document.querySelector('.shell');
    if (shell) { shell.setAttribute('aria-busy', value ? 'true' : 'false'); shell.className = 'shell' + (value ? ' is-loading' : ''); }
  }
  window.shopPreview = function (button, itemId) {
    var native = window.AionObject;
    if (!native || !native.ItemPreview) { message('Item Preview is available in Aion.', true); return false; }
    try { native.ItemPreview(+itemId); } catch (error) { message('Item Preview could not open.', true); }
    return false;
  };
  window.shopConfirm = function (form) {
    if (writing) return false;
    var button = form.querySelector('button');
    if (!button || button.disabled) return false;
    button.disabled = true; button.textContent = 'Purchasing...';
    return true;
  };
  window.shopAction = function (form) {
    if (writing) return false;
    var button = form.querySelector('button');
    if (!button || button.disabled) return false;
    button.disabled = true; form.className += ' submitting'; return true;
  };
  function encode(form) {
    var fields = form.elements, parts = [], i, f;
    for (i = 0; i < fields.length; i++) {
      f = fields[i];
      if (!f.name || f.disabled || ((f.type === 'checkbox' || f.type === 'radio') && !f.checked)) continue;
      parts.push(encodeURIComponent(f.name) + '=' + encodeURIComponent(f.value));
    }
    return parts.join('&');
  }
  function load(url, method, data, keepScroll, updateHistory) {
    if (writing) return false;
    ++serial;
    if (request) request.abort();
    if (timer) clearTimeout(timer);
    var current = serial, xhr = new XMLHttpRequest(), main = document.querySelector('.main-content');
    var scroll = keepScroll && main ? main.scrollTop : 0;
    request = xhr; writing = method === 'POST'; busy(true);
    function finish() { clearTimeout(timer); request = null; writing = false; busy(false); }
    function failure() {
      if (current !== serial) return;
      var wasWriting = writing;
      finish();
      message(wasWriting ? 'Confirmation unavailable. Check Kinah and Purchase history before another purchase.' : 'Cash Shop could not load. Select a category to try again.', true);
    }
    xhr.open(method, url.split('#')[0], true);
    if (method === 'POST') xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4 || current !== serial) return;
      if (!xhr.status || xhr.status >= 500) { failure(); return; }
      var markup = document.createElement('div'), html = xhr.responseText;
      var start = html.indexOf('<body>'), end = html.lastIndexOf('</body>');
      markup.innerHTML = start >= 0 && end > start ? html.substring(start + 6, end) : html;
      var nextMain = markup.querySelector('.main-content');
      if (!nextMain) { failure(); return; }
      var selectors = ['.main-content', '.side-nav', '.sidebar-top', '.shop-tabs'], i, from, to;
      for (i = 0; i < selectors.length; i++) {
        from = markup.querySelector(selectors[i]); to = document.querySelector(selectors[i]);
        if (from && to) to.innerHTML = from.innerHTML;
      }
      main = document.querySelector('.main-content'); main.scrollTop = scroll;
      var fragment = url.split('#')[1], target = fragment ? document.getElementById(fragment) : null;
      if (target && fragment !== 'shop-content' && !keepScroll) main.scrollTop = target.offsetTop;
      finish();
      if (updateHistory && window.history && history.pushState) history.pushState(null, '', url);
    };
    xhr.onerror = failure;
    timer = setTimeout(function () { if (current === serial) { xhr.onreadystatechange = null; xhr.abort(); failure(); } }, 20000);
    xhr.send(data || null);
    return false;
  }
  document.addEventListener('click', function (event) {
    var link = event.target;
    while (link && link.tagName !== 'A') link = link.parentNode;
    if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button > 0) return;
    if (link.className.indexOf('native-item-icon') >= 0) { event.preventDefault(); return; }
    if (link.pathname !== '/shop' || link.host !== location.host) return;
    event.preventDefault(); load(link.href, 'GET', null, false, true);
  }, false);
  document.addEventListener('submit', function (event) {
    var form = event.target;
    if (!form || form.tagName !== 'FORM' || event.defaultPrevented) return;
    event.preventDefault();
    var data = encode(form), method = form.method.toUpperCase();
    if (method === 'GET') load('/shop?' + data + '#shop-content', 'GET', null, false, true);
    else load(form.action, 'POST', data, !form.querySelector('[name="review"]') && !form.querySelector('.purchase-button'), false);
  }, false);
  // Changing a filter applies it directly; Enter still submits the search.
  document.addEventListener('change', function (event) {
    var field = event.target;
    if (field.tagName === 'SELECT' && field.form && field.form.className === 'finder')
      load('/shop?' + encode(field.form) + '#shop-content', 'GET', null, false, true);
  }, false);
  window.addEventListener('popstate', function () { load(location.href, 'GET', null, false, false); }, false);
}());
