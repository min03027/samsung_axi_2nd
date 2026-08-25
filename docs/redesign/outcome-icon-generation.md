# 과정 상세 결과물 아이콘 생성 기록

생성 자산은 `src/main/resources/static/v2/assets/outcome-icons/`에 저장했다. 각 PNG는 과정별 결과물 3개를 왼쪽부터 순서대로 배치한 투명 스프라이트다.

## UI/UX 세트에 사용한 프롬프트

```text
Use case: stylized-concept.
Asset type: transparent website icon sprite strip for three course outcomes.
Primary request: Create exactly three separate glossy 3D icons in one horizontal row, in this exact order:
1) user research insight — a friendly magnifying glass inspecting a small person card and sticky note,
2) clickable mobile prototype — a rounded smartphone with a tapping hand/cursor and connected interaction nodes,
3) UX case study — a premium portfolio binder with layered screens and a small presentation sparkle.
Reference guidance: use the attached icon collection only for its cute rounded toy-like 3D material, saturated color, beveling, and soft shadow. Use the attached webpage screenshot only as layout context for where each icon will appear.
Composition: wide transparent canvas divided mentally into three equal columns; one icon centered in each column; equal apparent size; generous padding; no overlap; consistent three-quarter front camera.
Style: premium Korean edtech, glossy polymer/clay, rounded beveled edges, tactile, playful but professional, studio lighting, subtle ambient shadow.
Palette: vivid purple and lavender with small coral-orange accents and white highlights.
Background: true transparent alpha. No colored rectangle, no scene, no backdrop.
Constraints: no text, no letters, no numbers, no logos, no watermark, no border, no panel, no labels. Exactly 3 icons.
```

## 나머지 세트에 사용한 프롬프트

아래 템플릿에 표의 `name`, `concepts`, `palette`와 하단의 그룹별 `background`, `constraints`를 그대로 대입했다.

```text
Use case: stylized-concept.
Asset type: transparent website icon sprite strip for {name}.
Create exactly three separate glossy 3D icons in one horizontal row, in this exact order: {concepts}.
Composition: wide transparent canvas; three equal columns; one icon centered in each column; equal apparent size; generous padding; no overlap; consistent three-quarter front camera.
Style: cute rounded toy-like 3D icon, glossy polymer/clay, deep bevels, tactile premium Korean edtech aesthetic, studio lighting, subtle ambient shadow.
Palette: {palette}.
Background: {background}
Constraints: {constraints}
```

- KDT 5종: `background` = `true transparent alpha, with no backdrop.` / `constraints` = `no text, letters, numbers, logos, watermark, border, frame, labels, or panels. Exactly 3 icons.`
- AI 영상·해외취업 3종: `background` = `true transparent alpha, no backdrop or scene.` / `constraints` = `no readable text, letters, numbers, logos, flags, watermark, border, frame, labels, or panels. Exactly 3 icons.`
- 일반고 5종·자격증 4종: `background` = `true transparent alpha, no backdrop or scene.` / `constraints` = `no readable text, letters, numbers, logos, watermark, border, frame, labels, or panels. Exactly 3 icons.`

| 파일 | name | concepts | palette |
|---|---|---|---|
| data.png | KDT data prediction course | 1) a connected database pipeline with flowing nodes, 2) an analytics dashboard tablet with bars and a decision pointer, 3) a personalized AI prediction crystal orb with a forward arrow | coral orange, deep charcoal black, warm cream, tiny mint accents |
| factory.png | KDT smart factory course | 1) linked factory machines and sensor nodes, 2) a real-time industrial dashboard with gauge and alert light, 3) a green smart factory with an automatic control dial and leaf | coral orange, deep charcoal black, warm cream, small emerald accents |
| aiot.png | KDT AIoT course | 1) an IoT sensor hub emitting wireless signals, 2) an anomaly-detection radar with a highlighted warning dot, 3) an operator decision tablet combining factory data and a check mark | coral orange, deep charcoal black, warm cream, small electric blue accents |
| robot.png | KDT robot AI course | 1) a robot vision camera with LiDAR scanning rings, 2) a tiny autonomous rover following a curved route, 3) a friendly industrial collaborative robot arm presenting a finished part | coral orange, deep charcoal black, warm cream, metallic silver accents |
| cloud.png | KDT cloud full-stack course | 1) a complete web application window connected to a database, 2) a cloud deployment package moving through a pipeline into a server, 3) a developer portfolio briefcase with layered code screens and a small trophy | coral orange, deep charcoal black, warm cream, small sky-blue accents |
| video.png | AI video editing course | 1) a vertical smartphone playing a dynamic short-form video with a sparkle, 2) a film clapperboard combined with a small megaphone for a brand promotion, 3) a professional video portfolio folder holding film strips and a play card | vivid purple, lavender, coral-pink, white highlights |
| japan.png | Japan Java developer career course | 1) an enterprise server application represented by connected server blocks and code brackets, 2) a polished technical portfolio folder with a small globe and developer screen, 3) two friendly interview speech bubbles with a handshake | electric blue, deep navy, white, small coral-orange accents |
| usa.png | USA global marketing course | 1) a globe with market analysis magnifier and chart, 2) a campaign performance dashboard with rising bars and target, 3) a global marketing portfolio briefcase with presentation cards | electric blue, deep navy, white, small coral-orange accents |
| china.png | China AI product manager course | 1) a product requirements clipboard with user cards and check marks, 2) an AI feature roadmap shown as connected milestone blocks and an upward route, 3) a global product launch globe with a rocket and strategy cards | electric blue, deep navy, white, small coral-orange accents |
| cooking.png | high-school culinary course | 1) an elegant Korean meal tray with rice bowl and side dishes, 2) a refined Western plated dish with sauce and chef garnish, 3) a chef toolkit with toque, knife, hygiene shield, and kitchen timer | emerald green, forest green, warm cream, small coral-orange accents |
| game.png | high-school game content course | 1) a game system design map with controller and connected level blocks, 2) a playable game screen with controller and cheerful character token, 3) a game production portfolio case with cartridge, trophy, and art cards | emerald green, forest green, warm cream, small coral-orange accents |
| design.png | high-school digital design course | 1) a brand graphics toolkit with poster, color swatches, and pen, 2) a video advertising clapperboard with megaphone and play card, 3) a graphic design portfolio book with a small certificate medal | emerald green, forest green, warm cream, small coral-orange accents |
| mobility.png | high-school smart mobility course | 1) a camera-and-sensor vehicle recognizing lanes and obstacles, 2) an autonomous driving chip connected to a curved route, 3) a compact smart mobility demo car with a presentation sparkle | emerald green, forest green, warm cream, small coral-orange accents |
| system.png | high-school information systems course | 1) a practical business application window connected to a database, 2) a protected server rack with backup cloud and shield, 3) a troubleshooting toolkit with log screen, wrench, and solved check mark | emerald green, forest green, warm cream, small coral-orange accents |
| adsp.png | data analysis certification course | 1) a connected data concept map with a small light bulb, 2) a stack of exam question cards with a pencil and check mark, 3) a personal mistake-review notebook with highlighted tabs and magnifying glass | coral orange, bright orange, charcoal black, warm cream, small gold accents |
| sqld.png | database SQL certification course | 1) a relational database model with linked cylinder tables, 2) a query-solving terminal represented by database blocks and code brackets, 3) a timed mock-exam board with checklist and certificate ribbon | coral orange, bright orange, charcoal black, warm cream, small gold accents |
| bigdata-cert.png | big data analyst certification course | 1) a big-data theory book connected to database cylinders, 2) an analysis laptop with data cleaning and model charts, 3) a timed mock-exam report with performance gauge and check mark | coral orange, bright orange, charcoal black, warm cream, small gold accents |
| engineer.png | information processing engineer certification course | 1) a software architecture blueprint with connected modules, 2) a coding-algorithm laptop with database and logic blocks, 3) a certification study plan board with medal and completed checklist | coral orange, bright orange, charcoal black, warm cream, small gold accents |
