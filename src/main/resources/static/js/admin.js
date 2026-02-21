document.addEventListener('DOMContentLoaded', () => {
  // 1) 최초 로딩
  loadEvents();

  // 2) 폼 submit 이벤트
  const form = document.getElementById('event-form');
  form.addEventListener('submit', createEvent);

  // 3) 이벤트 위임(목록 영역에서 버튼 클릭 처리)
  const listEl = document.getElementById('event-list');
  listEl.addEventListener('click', onEventListClick);
});

async function createEvent(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-create');
  btn.disabled = true;
  hideResult();

  const body = {
    name: document.getElementById('name').value.trim(),
    startAt: document.getElementById('startAt').value,
    endAt: document.getElementById('endAt').value,
    announceStartAt: document.getElementById('announceStartAt').value,
    announceEndAt: document.getElementById('announceEndAt').value,
    winnerPhone: document.getElementById('winnerPhone').value.trim(),
  };

  // 프론트에서 1차 검증
  if (body.endAt >= body.announceStartAt) {
    showResult('error', '참가 종료일은 발표 시작일보다 이전이어야 합니다.');
    btn.disabled = false;
    return;
  }

  try {
    const res = await fetch('/api/events', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    const data = await safeJson(res);
    if (!res.ok) throw new Error((data && data.error) || '등록 실패');

    // 등록 성공 시 메인으로 이동(원하면 loadEvents()로만 갱신해도 됨)
    window.location.href = '/';
  } catch (err) {
    showResult('error', err.message);
    btn.disabled = false;
  }
}

async function loadEvents() {
  const listEl = document.getElementById('event-list');
  listEl.innerHTML = '<p style="color:#999; text-align:center;">불러오는 중...</p>';

  try {
    const res = await fetch('/api/events?size=100');
    const data = await safeJson(res);
    const events = data?.events ?? [];

    if (!res.ok) throw new Error((data && data.error) || '목록 조회 실패');

    if (events.length === 0) {
      listEl.innerHTML = '<p style="color:#999; text-align:center;">등록된 이벤트가 없습니다.</p>';
      return;
    }

    listEl.innerHTML = events
      .map((ev) => {
        const status = String(ev.status || '').toUpperCase();
        const badgeClass = `badge-${String(status).toLowerCase()}`;

        const poolButton =
          status === 'READY'
            ? `<button class="btn-small" data-action="generate-pool" data-event-id="${ev.eventId}">풀 생성</button>`
            : '';

        return `
          <div class="event-item">
            <div class="event-item-info">
              <h3>${escapeHtml(ev.name)}</h3>
              <p>${formatDt(ev.startAt)} ~ ${formatDt(ev.endAt)}</p>
            </div>
            <div class="event-actions">
              <span class="badge ${badgeClass}">${statusLabel(status)}</span>
              ${poolButton}
            </div>
          </div>
        `;
      })
      .join('');
  } catch (err) {
    listEl.innerHTML = '<p style="color:#e74c3c; text-align:center;">목록을 불러올 수 없습니다.</p>';
  }
}

/**
 * 이벤트 위임 핸들러
 * - 목록 내부 클릭은 전부 여기서 잡고,
 * - data-action 보고 처리
 */
async function onEventListClick(e) {
  const btn = e.target.closest('button[data-action]');
  if (!btn) return;

  const action = btn.dataset.action;
  const eventId = btn.dataset.eventId;

  if (action === 'generate-pool') {
    await generatePool(eventId, btn);
  }
}

async function generatePool(eventId, btn) {
  btn.disabled = true;
  const prevText = btn.textContent;
  btn.textContent = '생성 중...';

  try {
    const res = await fetch(`/api/events/${eventId}/generate-pool`, { method: 'POST' });
    const data = await safeJson(res);
    if (!res.ok) throw new Error((data && data.error) || '풀 생성 실패');

    await loadEvents();
  } catch (err) {
    alert(err.message);
    btn.disabled = false;
    btn.textContent = prevText;
  }
}

function formatDt(dt) {
  if (!dt) return '';
  return String(dt).replace('T', ' ').substring(0, 16);
}

function statusLabel(status) {
  return { READY: '준비중', ACTIVE: '진행중', ENDED: '종료' }[status] || status;
}

function showResult(type, msg) {
  const el = document.getElementById('create-result');
  el.className = type === 'success' ? 'success-msg' : 'error';
  el.textContent = msg;
  el.classList.remove('hidden');
}

function hideResult() {
  document.getElementById('create-result').classList.add('hidden');
}

/**
 * res.json()이 실패(예: 204, 비정상 바디)해도 터지지 않게 안전 파서
 */
async function safeJson(res) {
  try {
    return await res.json();
  } catch (_) {
    return null;
  }
}

/**
 * 간단 XSS 방지용(이벤트명 같은 사용자 입력 렌더링 안전하게)
 */
function escapeHtml(str) {
  return String(str ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
