<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>당첨 결과 확인</title>
    <link rel="stylesheet" href="/css/lotto.css">
    <link rel="stylesheet" href="/css/result.css">
    <script src="/js/result.js" defer></script>
</head>
<body>
    <div class="container">
        <div class="card page-header">
            <h1>당첨 결과 확인</h1>
            <div id="event-name" class="event-name"></div>
            <div id="announce-period" class="announce-period"></div>
        </div>

        <!-- 폰번호 입력 -->
        <div id="step-check" class="card check-form">
            <h2>본인 확인</h2>
            <p>참가 시 입력한 휴대폰 번호를 입력해주세요.</p>
            <div class="input-group">
                <input type="tel" id="phone-number" placeholder="휴대폰 번호 (- 없이)" maxlength="11">
                <button id="btn-check" onclick="checkResult()">확인</button>
            </div>
        </div>

        <!-- 최초 확인 결과 (등수 공개) -->
        <div id="step-first-result" class="card result-card hidden">
            <div id="first-icon" class="result-icon"></div>
            <div id="first-tier" class="result-tier"></div>
            <div id="first-phone" class="result-phone"></div>
            <div id="first-numbers" class="lotto-numbers"></div>
            <div class="first-check-notice">
                최초 1회에 한해 당첨 등수와 로또 번호가 공개됩니다.<br>
                다음 조회부터는 당첨/미당첨 여부만 확인됩니다.
            </div>
            <button class="btn-retry" onclick="resetForm()">다른 번호로 확인</button>
        </div>

        <!-- 재확인 결과 (당첨/미당첨만) -->
        <div id="step-recheck-result" class="card result-card hidden">
            <div id="recheck-icon" class="result-icon"></div>
            <div id="recheck-status" class="result-status"></div>
            <div id="recheck-phone" class="result-phone"></div>
            <div class="recheck-notice">
                당첨 등수 및 로또 번호는 최초 확인 시에만 공개됩니다.
            </div>
            <button class="btn-retry" onclick="resetForm()">다른 번호로 확인</button>
        </div>

        <!-- 에러 -->
        <div id="error-message" class="error hidden"></div>

        <div class="back-link">
            <a href="/events">목록으로 돌아가기</a>
        </div>
    </div>
</body>
</html>
