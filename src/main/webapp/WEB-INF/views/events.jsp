<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>이벤트 안내</title>
    <link rel="stylesheet" href="/css/lotto.css">
    <link rel="stylesheet" href="/css/events.css">
    <script src="/js/events.js" defer></script>
</head>
<body>
    <div class="container">
        <div class="card page-header">
            <h1>이벤트 안내</h1>
            <p>진행 중인 이벤트에 참가해보세요!</p>
        </div>

        <div id="loading" class="card empty">이벤트 정보를 불러오는 중...</div>
        <div id="event-list"></div>
        <div id="pagination"></div>

        <div class="back-link">
            <a href="/">홈으로 돌아가기</a>
        </div>
    </div>
</body>
</html>
