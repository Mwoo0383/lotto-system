let eventId = null;
let verificationId = null;
let timerInterval = null;

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
        const res = await fetch(`/api/event/${eventId}`);
        if (!res.ok) throw new Error('이벤트를 찾을 수 없습니다.');
        const data = await res.json();
        document.getElementById('event-name').textContent = data.name;
        document.getElementById('event-period').textContent =
            `${data.startAt.replace('T', ' ')} ~ ${data.endAt.replace('T', ' ')}`;
    } catch (e) {
        showError(e.message);
    }
}

async function sendCode() {
    const phoneNumber = document.getElementById('phone-number').value.trim();
    if (!/^01[016789]\d{7,8}$/.test(phoneNumber)) {
        showError('올바른 휴대폰 번호를 입력해주세요.');
        return;
    }

    const btn = document.getElementById('btn-send-code');
    btn.disabled = true;
    btn.textContent = '발송 중...';
    hideError();

    try {
        const res = await fetch('/api/verification/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phoneNumber, eventId: Number(eventId) })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || '발송에 실패했습니다.');

        verificationId = data.verificationId;
        document.getElementById('step-verify').classList.remove('hidden');
        startTimer();
        btn.textContent = '재발송';
        btn.disabled = false;
    } catch (e) {
        showError(e.message);
        btn.textContent = '인증번호 발송';
        btn.disabled = false;
    }
}

async function verifyCode() {
    const code = document.getElementById('verify-code').value.trim();
    if (code.length !== 6) {
        showError('인증번호 6자리를 입력해주세요.');
        return;
    }

    const btn = document.getElementById('btn-verify');
    btn.disabled = true;
    hideError();

    try {
        const res = await fetch('/api/verification/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ verificationId, code })
        });
        const data = await res.json();
        if (!res.ok || !data.verified) throw new Error(data.error || '인증에 실패했습니다.');

        stopTimer();
        document.getElementById('step-phone').classList.add('hidden');
        document.getElementById('step-verify').classList.add('hidden');
        document.getElementById('step-participate').classList.remove('hidden');
    } catch (e) {
        showError(e.message);
        btn.disabled = false;
    }
}

async function participate() {
    const btn = document.getElementById('btn-participate');
    btn.disabled = true;
    btn.textContent = '처리 중...';
    hideError();

    const phoneNumber = document.getElementById('phone-number').value.trim();

    try {
        const res = await fetch('/api/lotto/participate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                phoneNumber,
                eventId: Number(eventId),
                verificationId
            })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || '참가에 실패했습니다.');

        document.getElementById('step-participate').classList.add('hidden');
        document.getElementById('step-result').classList.remove('hidden');
        document.getElementById('result-message').textContent =
            `${data.phoneLast4}님의 ${data.message}`;

        const numbersDiv = document.getElementById('lotto-numbers');
        numbersDiv.innerHTML = data.lottoNumbers
            .map(n => `<div class="lotto-ball">${n}</div>`)
            .join('');
    } catch (e) {
        showError(e.message);
        btn.disabled = false;
        btn.textContent = '로또 번호 받기';
    }
}

function startTimer() {
    let remaining = 180;
    const timerEl = document.getElementById('timer');
    updateTimerDisplay(timerEl, remaining);

    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        remaining--;
        updateTimerDisplay(timerEl, remaining);
        if (remaining <= 0) {
            stopTimer();
            showError('인증 시간이 만료되었습니다. 다시 발송해주세요.');
        }
    }, 1000);
}

function stopTimer() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}

function updateTimerDisplay(el, seconds) {
    const m = String(Math.floor(seconds / 60)).padStart(2, '0');
    const s = String(seconds % 60).padStart(2, '0');
    el.textContent = `${m}:${s}`;
}

function showError(msg) {
    const el = document.getElementById('error-message');
    el.textContent = msg;
    el.classList.remove('hidden');
}

function hideError() {
    document.getElementById('error-message').classList.add('hidden');
}
