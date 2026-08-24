(function () {
  'use strict';

  function logoFallback(item) {
    var fallback = document.createElement('span');
    fallback.className = 'organization-public__fallback';
    fallback.textContent = item.name;
    return fallback;
  }

  function organizationItem(item) {
    var element = item.websiteUrl ? document.createElement('a') : document.createElement('span');
    element.className = 'logorow__item organization-public__item';
    element.title = item.oneLineDescription || item.name;
    if (item.websiteUrl) {
      element.href = item.websiteUrl;
      element.target = '_blank';
      element.rel = 'noopener';
    }
    if (item.logoUrl) {
      var image = document.createElement('img');
      image.src = item.logoUrl;
      image.alt = item.name + ' 로고';
      image.loading = 'lazy';
      image.addEventListener('error', function () {
        image.replaceWith(logoFallback(item));
      });
      element.appendChild(image);
      var accessibleName = document.createElement('span');
      accessibleName.className = 'sr-only';
      accessibleName.textContent = item.name;
      element.appendChild(accessibleName);
    } else {
      element.appendChild(logoFallback(item));
    }
    return element;
  }

  function load(container) {
    var site = container.dataset.organizationSite;
    var position = container.dataset.organizationPosition;
    var params = new URLSearchParams();
    if (site) params.set('site', site);
    if (position) params.set('position', position);
    fetch('/v2/api/organizations?' + params.toString(), { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) throw new Error('organization request failed');
        return response.json();
      })
      .then(function (items) {
        container.replaceChildren();
        if (!items.length) {
          var empty = document.createElement('p');
          empty.className = 'organization-public__empty';
          empty.textContent = '공개된 기업·기관 정보가 없습니다.';
          container.appendChild(empty);
        } else {
          items.forEach(function (item) { container.appendChild(organizationItem(item)); });
        }
        container.setAttribute('aria-busy', 'false');
      })
      .catch(function () {
        container.replaceChildren();
        var message = document.createElement('p');
        message.className = 'organization-public__empty';
        message.textContent = '기업·기관 정보를 불러오지 못했습니다.';
        container.appendChild(message);
        container.setAttribute('aria-busy', 'false');
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-public-organizations]').forEach(load);
  });
})();
