(() => {
  const DAY = 24 * 60 * 60 * 1000;
  const labels = {
    open: '모집중',
    closing: '마감임박',
    rolling: '수시모집',
    'pre-recruiting': '사전모집',
    closed: '모집마감'
  };

  const seoulDate = date => {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).formatToParts(date).reduce((result, part) => {
      result[part.type] = part.value;
      return result;
    }, {});
    return `${parts.year}-${parts.month}-${parts.day}`;
  };

  const dateValue = value => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return NaN;
    return Date.parse(`${value}T00:00:00+09:00`);
  };

  const daysUntil = deadline => {
    const today = dateValue(seoulDate(new Date()));
    const due = dateValue(deadline);
    return Number.isFinite(due) ? Math.ceil((due - today) / DAY) : Infinity;
  };

  const derive = ({ adminStatus, deadline }) => {
    if (adminStatus === 'rolling') return 'rolling';
    if (adminStatus === 'pre-recruiting') return 'pre-recruiting';
    if (adminStatus !== 'recruiting') return 'closed';
    return daysUntil(deadline) <= 7 ? 'closing' : 'open';
  };

  window.AXI_RECRUITMENT_STATUS = { derive, daysUntil, labels };
})();
