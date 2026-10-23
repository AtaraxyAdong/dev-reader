# Reader API contract investigation

> Later authenticated debugging confirmed this route returns metadata only even with a valid
> plugin session. See [authenticated content repair](authenticated-reader-content-fix.md) for
> the current website content source and live evidence. The investigation below is historical.

Updated: 2026-10-20. Supersedes the initial candidate-parameter investigation in commit 3613099.

## Confirmed detail failure and contract correction

The actual API host is `https://api.mianshiya.com`; the route is `GET /api/question/get/vo`.
The query key is **questionId**, not id. Three anonymous, read-only requests for public question
`1968958432691322881` gave:

| Query | HTTP | Application code | Data |
| --- | --- | --- | --- |
| id=1968958432691322881 | 200 | 50000 | absent |
| questionId=1968958432691322881 | 200 | 0 | question VO |
| id=1968958432691322881&questionId=1968958432691322881 | 200 | 0 | question VO |

The successful response has the matching id, title, contentType, needVip, hasVipAuth and
other public metadata fields. This anonymous response omits content and bestQuestionAnswer;
it does not prove authenticated answer loading or complete body/answer mapping.
No credential, response Cookie or private account content was inspected or saved.

The initial implementation shipped a candidate id parameter despite its earlier probe
returning 50000. That unverified assumption is the confirmed cause of the universal detail
request failure. The adapter now sends questionId. An in-memory HTTP transport with real
Retrofit request generation and Gson conversion checks this boundary on every test run.

## Public-client evidence

The current served public JavaScript API module (53863) declares `/api/question/get/vo`,
passing a caller-supplied params object. The request wrapper (89427) prefixes
`https://api.mianshiya.com` and uses `credentials: include`. It maps 40100 to no login,
40101 to no access and 40110 to a login conflict. The existing plugin enum also declares
40102 as no VIP permission; this detail-route case is not verified against a real account.

The public question page reads `bestQuestionAnswer.content`. Existing plugin models and
public page data support contentType 0 = HTML and 1 = Markdown. The adapter preserves
missing, empty and unknown-format content distinctly and never substitutes community answers.
The legacy React website checkout at `E:\01-Java\mianshiya` is excluded as contract evidence.

## Remaining evidence limits

- Successful anonymous metadata fetch: verified. Authenticated body, full reference answer,
  VIP restrictions and account conflict: no live account validation performed.
- Relative image base, image authentication and bank navigation order: no new live validation.
- New HTTP/JSON tests are deterministic synthetic responses, not captured account fixtures.
- Native IDEA/JCEF rendering and QR login were not automated. The user reports successful
  QR login followed by automatic login-dialog closure; this is consistent with the existing
  successful-user callback, not evidence of a QR failure.

For the independent login presentation defect and validation results, see
[login/detail repair](login-detail-fix-validation.md).
