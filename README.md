# AUTEDI SERVER
영어 도서관 및 영어 교육기관의 학생 글쓰기 첨삭 업무를 효율화하는 AI 기반 첨삭 서비스 `AUTEDI`의 백엔드 서버입니다.

## 아키텍처

```mermaid
flowchart LR
  User([사용자 브라우저])
  subgraph Vercel
    FE["Frontend<br/>www.autedi.co.kr"]
  end
  subgraph EC2["AWS EC2 · Docker Compose"]
    NGINX["Nginx<br/>SSL · 도메인 라우팅"]
    APP["Spring Boot<br/>api.autedi.co.kr"]
    DB[("MySQL 8.0")]
  end
  S3[("AWS S3")]
  MAIL["Gmail SMTP"]
  LLM["OpenAI Responses API<br/>(AI 첨삭)"]

  User --> FE
  FE -->|HTTPS| NGINX
  NGINX --> APP
  APP --> DB
  APP --> S3
  APP --> MAIL
  APP --> LLM
```

## 팀원

<table>
  <tr>
    <td align="center"><a href="https://github.com/zzulYH-11"><b>전유하</b></a></td>
    <td align="center"><a href="https://github.com/taiho0303-blip"><b>정태호</b></a></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/zzulYH-11.png" width="120" /></td>
    <td align="center"><img src="https://github.com/taiho0303-blip.png" width="120" /></td>
  </tr>
  <tr>
    <td align="center">Backend Developer</td>
    <td align="center">Backend Developer</td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/zzulYH-11">@zzulYH-11</a></td>
    <td align="center"><a href="https://github.com/taiho0303-blip">@taiho0303-blip</a></td>
  </tr>
</table>
