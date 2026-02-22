let currentPage = 1;

document.addEventListener('DOMContentLoaded', () => loadEvents(1));

async function loadEvents(page) {
    try {
        const res = await fetch(`/api/events?page=${page}&size=5`);
        const data = await res.json();
        document.getElementById('loading').classList.add('hidden');

        const events = data.events;
        currentPage = data.page;

        if (events.length === 0) {
            document.getElementById('event-list').innerHTML =
                '<div class="card empty">등록된 이벤트가 없습니다.</div>';
            document.getElementById('pagination').innerHTML = '';
            return;
        }

        document.getElementById('event-list').innerHTML = renderEvents(events);
        renderPagination(data.page, data.totalPages);
    } catch (e) {
        document.getElementById('loading').textContent = '이벤트 정보를 불러올 수 없습니다.';
    }
}

function renderEvents(events) {
    let html = '';
    for (const ev of events) {
        const now = new Date();
        const inEvent = ev.startAt && ev.endAt
            && now >= new Date(ev.startAt) && now <= new Date(ev.endAt);
        const inAnnounce = ev.announceStartAt && ev.announceEndAt
            && now >= new Date(ev.announceStartAt) && now <= new Date(ev.announceEndAt);

        const afterEvent = ev.endAt && now > new Date(ev.endAt);
        const beforeAnnounce = ev.announceStartAt && now < new Date(ev.announceStartAt);
        const inReview = afterEvent && beforeAnnounce;

        let badge = '';
        if (inEvent) {
            badge = '<span class="badge badge-active">참가 가능</span>';
        } else if (inReview) {
            badge = '<span class="badge badge-review">당첨자 확인중</span>';
        } else if (inAnnounce) {
            badge = '<span class="badge badge-announcing">당첨 확인</span>';
        } else {
            badge = '<span class="badge badge-ended">이벤트 종료</span>';
        }

        // 기간 표시
        let periods = `<div class="${inEvent ? 'current' : ''}"><span class="label">참가 기간</span>${formatDate(ev.startAt)} ~ ${formatDate(ev.endAt)}</div>`;
        if (ev.announceStartAt && ev.announceEndAt) {
            periods += `<div class="${inAnnounce ? 'current' : ''}"><span class="label">발표 기간</span>${formatDate(ev.announceStartAt)} ~ ${formatDate(ev.announceEndAt)}</div>`;
        }

        // 버튼: 기간에 따라 하나만
        let actions = '';
        if (inEvent) {
            actions = `<div class="event-actions">
                <a href="/participate.html?eventId=${ev.eventId}" class="btn-participate">참가하기</a>
            </div>`;
        } else if (inAnnounce) {
            actions = `<div class="event-actions">
                <a href="/result.html?eventId=${ev.eventId}" class="btn-result">결과보기</a>
            </div>`;
        }

        html += `
            <div class="card event-item">
                <div class="event-header">
                    <span class="event-name">${ev.name}</span>
                    ${badge}
                </div>
                <div class="event-periods">${periods}</div>
                ${actions}
            </div>`;
    }
    return html;
}

function renderPagination(page, totalPages) {
    if (totalPages <= 1) {
        document.getElementById('pagination').innerHTML = '';
        return;
    }
    let html = '<div class="pagination">';
    html += `<button ${page <= 1 ? 'disabled' : ''} onclick="loadEvents(${page - 1})">&lt;</button>`;
    for (let i = 1; i <= totalPages; i++) {
        html += `<button class="${i === page ? 'active' : ''}" onclick="loadEvents(${i})">${i}</button>`;
    }
    html += `<button ${page >= totalPages ? 'disabled' : ''} onclick="loadEvents(${page + 1})">&gt;</button>`;
    html += '</div>';
    document.getElementById('pagination').innerHTML = html;
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    return dateStr.replace('T', ' ').substring(0, 16);
}