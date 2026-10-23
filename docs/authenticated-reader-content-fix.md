# Authenticated reader content repair (0.5.8)

Date: 2026-10-20. Supersedes the authenticated-content evidence limits in
`reader-api-contract.md` and `login-detail-fix-validation.md`.

## Observed failure

The running IC 2025.2.6.3 sandbox was logged in by the user. JDI breakpoints inspected
response status, field presence and permission flags without logging credentials or answer text.
For several public questions, `GET /api/question/get/vo?questionId=...` returned HTTP 200,
business code 0 and `hasVipAuth=true`, but omitted both `content` and `bestQuestionAnswer`.
`GET /api/user/get/login` using the same plugin client also succeeded. Therefore the placeholders
were caused by an incomplete content source, not a failed login or Markdown renderer.

A read-only request with that same plugin session to the canonical question webpage returned
`initialQuestionDetail` with `hasVipAuth=true` and `bestQuestionAnswer`. Its content was a
Next Flight text-record reference. The user's logged-in web tab showed the complete answer
from the same server-rendered data path. Anonymous pages can contain only a short preview.

## Change

Keep the existing metadata API and bank navigation. When a content field is absent, fetch
`https://www.mianshiya.com/question/<id>` with the existing plugin session. Do not copy browser
credentials. Disable redirects for this request, bound response size to 4 MiB, and use a
30-second overall timeout. No response body, session or answer text is logged.

Parse the page's Next Flight data without executing JavaScript. Resolve referenced JSON/text
records, including UTF-8 byte lengths, and verify the exact question ID. Use only the page's
question and recommended answer; community answers are not a substitute. Feed the extracted
HTML/Markdown into the existing sanitization and Markdown rendering pipeline. Website styles,
scripts and UI are not loaded into the reading pane.

Require an explicit authorized page state before displaying its answer. Missing permissions
or malformed page data produce an error. An unauthorized free-question page requests login;
an unauthorized VIP question reports restricted access. A missing supplementary prompt in an
authoritative authorized snapshot is empty; an advertised but absent answer remains unavailable.

## Verification

Regression tests cover metadata-only API -> full page answer, guest/VIP previews, absent
permissions, missing versus empty answer, HTTP errors, oversized/challenge pages, and Flight
text/reference parsing. Fixtures are synthetic and contain no account information.

Final verification: `check verifyPlugin buildPlugin runPluginVerifier` completed with
BUILD SUCCESSFUL. All 58 tests in 10 suites passed. Plugin Verifier reports Compatible
against IC-252.28539.97 (Community 2025.2.6.3), with the same three existing deprecated or
scheduled-removal API usages. The exact company installation 2025.2.6.1 was not rerun here.

The debug sandbox was restarted with 0.5.8 and its existing login session. The same previously
failing question, `1831172728330137601` (server-side rendering/SSR), now shows the recommended
answer's main points, expanded explanation and code block. Dragging the right sidebar from
approximately 300 to 500 logical pixels reflows the text. Returning to the list preserves the
search and selection; reopening the question displays the answer again. Left/bottom docking
were not rechecked in this content-only fix. No production website, account or permissions
were changed. The debug IDE remains open; JDWP is bound to 127.0.0.1:5005.

Local package: `build/distributions/Mianshiya-0.5.8.zip`. No release publication was performed.

## Maintenance boundary

Next Flight page data is a website implementation detail, not a guaranteed public API contract.
If the website changes the payload or access flags, the reader reports an explicit parse/access
failure and keeps the source link available. Recheck the live page contract before adapting it.
