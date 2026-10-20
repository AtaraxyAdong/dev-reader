# Reader API contract investigation

Date: 2026-10-20
Plugin baseline: `98d5a90901ba7b78c2fed8923b270e284db3a48d`

## Status

The public content contract is **not verified**. Do not mark real question loading complete or infer a detail endpoint from the old website checkout.

## Public unauthenticated page observation

Opened `https://www.mianshiya.com/question/2031664941567598594` in a clean in-app browser session, without accessing browser credentials. The rendered page showed the question title and body, a “推荐答案” panel, and the explicit “点击登录查看完整内容” gate in that answer panel. This establishes that the public page can expose some answer preview while gating the complete answer. It does not establish the network endpoint, response fields, body completeness, source format, or permission response semantics.

Further browser-based inspection was blocked in the subagent browser environment: creating an in-app browser tab returned “IAB visibility is not supported in a subagent thread.” The web fetcher returned status 567 for the page. No login was attempted, and no credentials or response cookies were accessed.

Public HTML and the referenced question-page JavaScript were subsequently fetched without credentials. The current question page bundle reads `question.bestQuestionAnswer.content` for its recommended-answer panel and renders “点击登录查看完整内容” when the user is logged out. Its shared API module declares `GET /api/question/get/vo`, passing a caller-supplied params object. The API wrapper uses same-origin `/api` paths with `credentials: include`; this does not confirm that the plugin's existing API host is still the correct host. The wrapper recognizes application codes `40100` (no login), `40101` (no access), and `40110` (account active on another device; login required).

The public page's server-rendered data contains `contentType: 0` for the question and `contentType: 1` for `bestQuestionAnswer`. The page bundle and existing Java response model agree that answer content type 0 is rich text and 1 is Markdown; interpreting the question's same numeric field with those semantics is an inference from the shared page data/model conventions. The page's answer UI remains login-gated, so its response text was not used as a fixture or exposed in this report.

A read-only request to the evidenced route on the plugin's existing host, `https://api.mianshiya.com/api/question/get/vo?id=2031664941567598594`, returned HTTP 200 JSON with application code `50000` and message `系统错误`, without data. The candidate query key `id` is not validated by this response; no `questionId` retry was made. The same route on `www.mianshiya.com` timed out. Thus the path is verified, while host compatibility, query argument, successful response mapping, body completeness, and route-specific permission behavior remain unverified.

## Repository evidence (not a live contract)

- The plugin API declares list/search calls and `question_answer/list/by_question`; it has no question-detail method.
- Current served public-client JS declares `GET /api/question/get/vo` as a question VO fetch and reads `bestQuestionAnswer.content` for the recommended-answer panel.
- `Question` has `content` and `bestQuestionAnswer` fields, but model declarations alone do not prove these fields are present or complete in current responses.
- `QuestionAnswer.contentType` documents `0` as rich text and `1` as Markdown; this does not verify the current source or answer field format.
- The plugin declares `40100` as not logged in and `40102` as no VIP authorization. Their mappings for the current reader source are unverified.
- The website checkout at `E:\01-Java\mianshiya` contains a legacy `/question/get` call. It is explicitly excluded as evidence of the current public client contract.
- Community answer listing is not accepted as the reference answer source.

## Unverified fields and cases

| Case | Current evidence | Status |
| --- | --- | --- |
| Complete question body | Public page renders a body; no authoritative response captured | Unverified |
| Reference-answer source and completeness | Public client identifies `bestQuestionAnswer.content`; logged-out page gates complete content | Field identity verified; completeness and API response content remain unverified |
| Markdown vs rich-text formats | Public rendered data reports question `contentType: 0` and best answer `contentType: 1`; existing Java answer model defines 0=rich text, 1=Markdown | Answer mapping supported; question mapping is an explicit inference |
| Relative resource base | No response or client bundle inspected | Unverified |
| Missing vs empty answer | No authoritative response captured | Unverified |
| Forbidden answer with readable body | Page indicates a login gate, but no API response captured | Unverified |
| Global authentication codes | Public API wrapper maps 40100 to no-login, 40101 to no-access, and 40110 to login-conflict; the plugin's existing enum also declares 40102 as no-VIP authorization | 40100/40101/40110 wrapper semantics verified; 40102 comes from the existing plugin enum; detail-route behavior and response fixtures unverified |
| Navigation order | Existing `do_question_info` declaration not checked against live response | Unverified |

The only captured API response is the sanitized public-route system-error response (`50000`, no data). No successful or authorization-state API fixtures have been captured. Synthetic mapper tests are test doubles and do not satisfy the plan's real-contract acceptance condition.

## Implementation decision

The Java adapter uses the public-client path `/question/get/vo` with candidate `id` query key on the plugin's configured API host. The API path and body/answer content-type values are evidenced; the host, query key, success response, and route-specific permission mapping still require live validation. The adapter maps unknown formats to `UNKNOWN`, absent answer data to `UNAVAILABLE`, explicit empty answer content to `EMPTY`, and does not use community answers as a fallback. No current claim of end-to-end live loading is warranted.
