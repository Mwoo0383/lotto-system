document.addEventListener('DOMContentLoaded', async () => {
    const today = new Date().toISOString().slice(0, 10);
    const visitKey = 'lastVisitDate';
    const lastVisit = localStorage.getItem(visitKey);

    // 오늘 이미 방문했으면 리다이렉트 안 함
    if (lastVisit === today) return;

    try {
        // 1️⃣ 발표 기간 우선 체크
        const announceRes = await fetch('/api/event/announcing');
        const announceData = await announceRes.json();

        if (announceData.announcing) {
            localStorage.setItem(visitKey, today);
            location.href = `/result.html?eventId=${announceData.eventId}`;
            return;
        }

        // 2️⃣ 이벤트 진행 중 체크
        const res = await fetch('/api/event/active');
        const data = await res.json();

        if (data.active) {
            localStorage.setItem(visitKey, today);
            location.href = '/events.html';
        }
    } catch (e) {
        // 네트워크 오류 등은 무시 (메인 화면 유지)
        console.warn('index redirect check failed', e);
    }
});