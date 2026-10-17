/* Shared ES5 quantity control, extracted from Central Market. */
(function (global) {
  'use strict';
  global.AionQuantity = {
    markup: function (max, initial, extra, money) {
      return '<label for="quantity">Quantity · maximum ' + money(max) + '</label><input id="quantity" name="quantity" type="text" value="' + initial + '" maxlength="12"><div class="quantity-tools"><button id="max-quantity" type="button">Max</button>' + (extra || '') + '</div>';
    },
    value: function (input, max) {
      var value = input ? input.value : '';
      return /^\d+$/.test(value) && +value >= 1 && +value <= max ? +value : 0;
    },
    bindMax: function (input, button, max) {
      if (!input || !button) return;
      button.onclick = function () {
        input.value = max;
        var event = document.createEvent('Event');
        event.initEvent('input', true, true); input.dispatchEvent(event);
      };
    }
  };
}(window));
