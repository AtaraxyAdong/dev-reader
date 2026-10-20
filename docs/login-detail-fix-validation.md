# Login presentation and question detail repair (0.5.7)

Date: 2026-10-20. Repository: `E:\01-Java\mianshiya-plugin`.
Branch: `codex/sidebar-markdown-reader`. Starting commit: f7b7240.

## User reproduction evidence

The two supplied screenshots were materialized from Library and viewed. They show IDEA
Community 2025.2.6.1 and the reader's generic detail error. The supplied About output identifies
IC-252.28539.33, Windows 11, JBR 21.0.9 and JCEF 122.1.9, with plugin 0.5.6.
This is within the unchanged plugin range 213 through 252.*. The user confirms QR login
closes the login dialog, and question loading fails both before and after login.

## Independent findings and fixes

1. The detail request sent `id`; the live API requires `questionId`. Anonymous same-question
   probes returned business code 50000 for id and code 0 for questionId. Correct the query key.
2. The new sidebar owns private actions, while the legacy login callback updates a globally
   registered toolbar. The sidebar always offered Login regardless of saved verified user.
   Its action now reads current user/cookie presence and shows a disabled logged-in label;
   an explicit expired-session result keeps re-login available. No account name is displayed.
3. DateTypeAdapter called nextString for JSON null. A real Retrofit/Gson boundary regression
   failed for a null vipExpireTime in an answer author. Consume null correctly; cover login
   user deserialization too. This is a demonstrated defect, not a proven explanation of the
   user's specific successful QR session.
4. The UI discarded repository error messages. Show only fixed failure categories and numeric
   HTTP/business codes. Never show raw server messages, exception messages, headers or URLs.
   The reader handles response codes directly without the interactive response interceptor.
5. Remove CookieManager's plaintext Cookie/URL logs; validate HTTP/business success and a
   non-null user id before accepting user information. Logs contain only fixed categories or
   numeric status codes. The existing credential persistence mechanism is unchanged.

## Validation

Targeted regression: four HTTP/JSON tests failed on the original implementation. They exercise
real Retrofit URL construction and Gson conversion with an in-memory HTTP transport, including
safe business/HTTP errors and malformed responses. Two additional presentation/action tests
cover login, logout and explicit re-login after expiry. They do not launch native JCEF.

Full verification completed successfully on ADONGSTRIX using the existing private JDK 17:

```powershell
.\gradlew.bat --no-daemon --offline --rerun-tasks check verifyPlugin buildPlugin runPluginVerifier `
  '-PreaderVerifierIde=.superpowers/ide/community-2025.2.6.3' `
  '-PreaderVerifierRuntime=.superpowers/ide/community-2025.2.6.3/jbr' `
  --console=plain '-Dfile.encoding=UTF-8'
```

Result: BUILD SUCCESSFUL; all 18 tasks executed. Eight JUnit suites, 38 tests, zero failures,
errors or skipped tests. Plugin Verifier: Compatible against IC-252.28539.97, with the same
three pre-existing deprecated/scheduled-removal API warnings (JBCefJSQuery.create,
ActionToolbar.setLayoutPolicy and legacy BrowserFileEditor JBCefCookieManager.setCookie).
The verifier target is 2025.2.6.3, not the user's exact 2025.2.6.1 installation.
Native runtime behavior on the user's IDE remains a separate manual check.

## Manual acceptance still required

No native desktop automation is available in this environment. Automated tests and Plugin
Verifier cannot establish successful authenticated loading on the user's running IDE.
After installing 0.5.7 and restarting IDEA, verify: login label changes after QR success;
open a public question; open an entitled answer; check image rendering; log out and verify
login controls return. If loading fails, report only the displayed numeric code/category
and the public question id. No Cookie or account details are needed.

No default-branch change, Release publication or IDEA compatibility expansion is part of this fix.
