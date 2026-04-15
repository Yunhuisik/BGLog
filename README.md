🎮 BGLog

PUBG 전적을 조회하고 분석할 수 있는 웹 서비스



🛠 사용 기술

Backend  : Spring Boot (Java)

Frontend : HTML / CSS / JavaScript

Infra    : Render, GitHub Actions

API      : PUBG Official API



🚀 실행 방법

https://bglog.onrender.com/


💡 만든 이유 / 목표

단순 전적 검색이 아닌 원하는 유저와의 비교, 파티설정과 같은 기능을 사용하고자 개발했습니다.

기본 검색기능부터 유저간 비교기능을 바탕으로 파티 설정, 내기 모드 등과 같은 차별성 있는 재미요소를 추가하고자 합니다.

또한, 검색 속도 개선을 위해 계속해서 노력하여 사용하기에 불편함이 없는 사이트를 제작해보고 싶습니다.


⚙️ 주요 구현 내용

1. API 호출 최적화
   
PUBG API Rate Limit 고려

불필요한 호출 제거 및 최소화



2. 검색 속도 개선

반복적인 호출을 병렬처리함으로서 속도 개선   


📌 배운 점

외부 API를 사용할 때는 기능보다 제약 조건을 먼저 고려해야 한다는 점

데이터를 그대로 보여주는 것이 아니라 가공해서 전달하는 것이 중요하다는 점

사용자 입장에서 이해하기 쉬운 UI/UX 설계의 중요성


🔗 링크

📁 GitHub : https://github.com/Yunhuisik/BGLog

🌐 Demo : https://bglog.onrender.com/
