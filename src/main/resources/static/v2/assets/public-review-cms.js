(function () {
  'use strict';

  function element(tag, className, text) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }

  function meta(item) {
    return [item.completionYear ? item.completionYear + ' 수료' : null,
      item.jobTitle, item.employmentCompany].filter(Boolean).join(' · ');
  }

  function reviewCard(item) {
    var article = element('article', 'review public-review-card');
    if (item.imageUrl) {
      var image = element('img', 'public-review-card__image');
      image.src = item.imageUrl; image.alt = item.authorDisplayName + ' 후기 이미지'; image.loading = 'lazy';
      article.appendChild(image);
    }
    article.appendChild(element('span', 'badge badge--brand', item.contentTypeLabel));
    article.appendChild(element('h3', 'public-review-card__title', item.title));
    article.appendChild(element('p', 'review__quote', '“' + item.content + '”'));
    var who = element('div', 'review__who');
    who.appendChild(element('span', 'avatar', item.authorDisplayName.charAt(0) || '후'));
    var info = element('div', 'review__meta');
    info.appendChild(element('b', '', item.authorDisplayName + (item.cohort ? ' · ' + item.cohort : '')));
    info.appendChild(element('span', '', meta(item) || item.courseName || '수료생 후기'));
    who.appendChild(info); article.appendChild(who);
    var links = element('div', 'public-review-card__links');
    var detail = element('a', 'story-text-link', '인터뷰 자세히 보기 →'); detail.href = item.detailUrl; links.appendChild(detail);
    if (item.courseUrl) { var course = element('a', 'story-text-link', '관련 과정 보기'); course.href = item.courseUrl; links.appendChild(course); }
    article.appendChild(links);
    return article;
  }

  function storyReview(item) {
    var article = element('article', '');
    article.appendChild(element('p', '', '“' + item.content + '”'));
    article.appendChild(element('b', '', item.authorDisplayName + ' · ' + (item.jobTitle || item.courseName || '수료생')));
    var link = element('a', '', '자세히 보기 →'); link.href = item.detailUrl; article.appendChild(link);
    return article;
  }

  function empty(container, message) {
    container.replaceChildren(element('p', 'public-review-empty', message || '공개된 후기가 없습니다.'));
    container.setAttribute('aria-busy', 'false');
  }

  function fetchReviews(params) {
    return fetch('/v2/api/reviews?' + params.toString(), { headers: { Accept: 'application/json' } })
      .then(function (response) { if (!response.ok) throw new Error('review request failed'); return response.json(); });
  }

  function loadGrid(container) {
    var params = new URLSearchParams();
    if (container.dataset.reviewSite) params.set('site', container.dataset.reviewSite);
    if (container.dataset.reviewPosition) params.set('position', container.dataset.reviewPosition);
    if (container.dataset.reviewFeatured) params.set('featured', container.dataset.reviewFeatured);
    if (container.dataset.reviewCourseFromQuery === 'true') {
      var courseId = new URLSearchParams(location.search).get('courseId');
      if (courseId) params.set('courseId', courseId);
    }
    fetchReviews(params).then(function (items) {
      var limit = Number(container.dataset.reviewLimit || 0);
      if (limit > 0) items = items.slice(0, limit);
      if (!items.length) return empty(container, container.dataset.reviewEmpty);
      container.replaceChildren();
      items.forEach(function (item) { container.appendChild(container.dataset.reviewMode === 'story' ? storyReview(item) : reviewCard(item)); });
      container.setAttribute('aria-busy', 'false');
    }).catch(function () { empty(container, '후기 정보를 불러오지 못했습니다.'); });
  }

  function addOptions(select, items, value, label) {
    var seen = new Set();
    items.forEach(function (item) {
      var key = value(item), text = label(item);
      if (key == null || !text || seen.has(String(key))) return;
      seen.add(String(key)); var option = element('option', '', text); option.value = key; select.appendChild(option);
    });
  }

  function loadCatalog(root) {
    var params = new URLSearchParams({ site: 'CAMPUS' });
    fetchReviews(params).then(function (items) {
      var company = root.querySelector('[data-review-filter-company]');
      var job = root.querySelector('[data-review-filter-job]');
      var course = root.querySelector('[data-review-filter-course]');
      var year = root.querySelector('[data-review-filter-year]');
      addOptions(company, items, function (i) { return i.organizationId; }, function (i) { return i.employmentCompany; });
      addOptions(job, items, function (i) { return i.jobTitle; }, function (i) { return i.jobTitle; });
      addOptions(course, items, function (i) { return i.courseId; }, function (i) { return i.courseName; });
      addOptions(year, items, function (i) { return i.completionYear; }, function (i) { return i.completionYear ? i.completionYear + '년' : null; });
      var grid = root.querySelector('[data-review-catalog-grid]');
      function render() {
        var filtered = items.filter(function (item) {
          return (!company.value || String(item.organizationId) === company.value)
            && (!job.value || item.jobTitle === job.value)
            && (!course.value || String(item.courseId) === course.value)
            && (!year.value || String(item.completionYear) === year.value);
        });
        if (!filtered.length) return empty(grid, '선택한 조건에 맞는 후기가 없습니다.');
        grid.replaceChildren(); filtered.forEach(function (item) { grid.appendChild(reviewCard(item)); });
        grid.setAttribute('aria-busy', 'false');
      }
      [company, job, course, year].forEach(function (select) { select.addEventListener('change', render); });
      render();
    }).catch(function () { empty(root.querySelector('[data-review-catalog-grid]'), '후기 정보를 불러오지 못했습니다.'); });
  }

  function loadDetail(root) {
    var id = new URLSearchParams(location.search).get('id');
    if (!id) { root.replaceChildren(element('p', 'public-review-empty', '후기를 찾을 수 없습니다.')); return; }
    fetch('/v2/api/reviews/' + encodeURIComponent(id), { headers: { Accept: 'application/json' } })
      .then(function (response) { if (!response.ok) throw new Error('not found'); return response.json(); })
      .then(function (item) {
        root.querySelector('[data-review-detail-type]').textContent = item.contentTypeLabel;
        root.querySelector('[data-review-detail-title]').textContent = item.title;
        root.querySelector('[data-review-detail-content]').textContent = item.content;
        root.querySelector('[data-review-detail-author]').textContent = item.authorDisplayName;
        root.querySelector('[data-review-detail-meta]').textContent = meta(item) || item.courseName || '';
        var image = root.querySelector('[data-review-detail-image]');
        if (item.imageUrl) { image.src = item.imageUrl; image.alt = item.authorDisplayName + ' 후기 이미지'; image.hidden = false; }
        var video = root.querySelector('[data-review-detail-video]');
        if (item.videoUrl) { video.href = item.videoUrl; video.hidden = false; }
        [['preTrainingSituation','before'],['courseExperience','course'],['projectExperience','project'],['employmentJourney','employment'],['currentRoleDetail','role']].forEach(function (pair) {
          var section = root.querySelector('[data-review-detail-' + pair[1] + ']');
          if (item[pair[0]]) { section.querySelector('p').textContent = item[pair[0]]; section.hidden = false; }
        });
        var courseLink = root.querySelector('[data-review-detail-course-link]');
        if (item.courseUrl) { courseLink.href = item.courseUrl; courseLink.textContent = (item.courseName || '관련 과정') + ' 보기 →'; courseLink.hidden = false; }
        root.setAttribute('aria-busy', 'false');
      }).catch(function () { root.replaceChildren(element('p', 'public-review-empty', '공개된 후기를 찾을 수 없습니다.')); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-public-review-grid]').forEach(loadGrid);
    document.querySelectorAll('[data-review-catalog]').forEach(loadCatalog);
    document.querySelectorAll('[data-review-detail]').forEach(loadDetail);
  });
})();
