let eventId = null;

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    eventId = params.get('eventId');
    if (!eventId) {
        showError('eventId가 필요합니다.');
        return;
    }
    loadEvent();
});

async function loadEvent() {
    try {
        const res = await fetch(`/api/events/${eventId}`);
        if (!res.ok) throw new Error('이벤트를 찾을 수 없습니다.');
        const data = await res.json();
        document.getElementById('event-name').textContent = data.name;
        if (data.announceStartAt && data.announceEndAt) {
            document.getElementById('announce-period').textContent =
                '발표 기간: ' + formatDate(data.announceStartAt) + ' ~ ' + formatDate(data.announceEndAt);
        }
    } catch (e) {
        showError(e.message);
    }
}

async function checkResult() {
    const phoneNumber = document.getElementById('phone-number').value.trim();
    if (!/^01[016789]\d{7,8}$/.test(phoneNumber)) {
        showError('올바른 휴대폰 번호를 입력해주세요.');
        return;
    }

    const btn = document.getElementById('btn-check');
    btn.disabled = true;
    btn.textContent = '확인 중...';
    hideError();

    try {
        const res = await fetch('/api/lotto/result', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phoneNumber, eventId: Number(eventId) })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || '조회에 실패했습니다.');

        document.getElementById('step-check').classList.add('hidden');

        if (data.firstCheck) {
            showFirstResult(data);
        } else {
            showRecheckResult(data);
        }
    } catch (e) {
        showError(e.message);
        btn.disabled = false;
        btn.textContent = '확인';
    }
}

function showFirstResult(data) {
    const section = document.getElementById('step-first-result');

    document.getElementById('first-icon').textContent = data.won ? '🎉' : '😔';

    const tierEl = document.getElementById('first-tier');
    tierEl.textContent = data.resultLabel;
    tierEl.className = 'result-tier ' + (data.won ? 'won' : 'lost');

    document.getElementById('first-phone').textContent = data.phoneLast4 + '님의 결과';

    if (data.lottoNumbers && data.lottoNumbers.length > 0) {
        document.getElementById('first-numbers').innerHTML =
            data.lottoNumbers.map(n => `<div class="lotto-ball">${n}</div>`).join('');
    }

    section.classList.remove('hidden');
}

function showRecheckResult(data) {
    const section = document.getElementById('step-recheck-result');

    document.getElementById('recheck-icon').textContent = data.won ? '🎉' : '😔';

    const statusEl = document.getElementById('recheck-status');
    statusEl.textContent = data.won ? '당첨' : '미당첨';
    statusEl.className = 'result-status ' + (data.won ? 'won' : 'lost');

    document.getElementById('recheck-phone').textContent = data.phoneLast4 + '님의 결과';

    section.classList.remove('hidden');
}

function resetForm() {
    document.getElementById('step-first-result').classList.add('hidden');
    document.getElementById('step-recheck-result').classList.add('hidden');
    document.getElementById('step-check').classList.remove('hidden');
    document.getElementById('phone-number').value = '';
    document.getElementById('btn-check').disabled = false;
    document.getElementById('btn-check').textContent = '확인';
    hideError();
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    return dateStr.replace('T', ' ').substring(0, 16);
}

function showError(msg) {
    const el = document.getElementById('error-message');
    el.textContent = msg;
    el.classList.remove('hidden');
}

function hideError() {
    document.getElementById('error-message').classList.add('hidden');
}