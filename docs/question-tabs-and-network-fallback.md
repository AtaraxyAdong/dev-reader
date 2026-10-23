# Question tabs and network fallback (0.5.10)

Date: 2026-10-20.

## Requested behavior

The sidebar's fixed 阅读 tab keeps the bank/question list, search, filters and current selection.
Opening a question creates a closeable native IDEA content tab named after the question. Opening
the same ID again selects its existing tab without reloading or losing scroll/folding state.
Previous/next replaces the question inside the originating tab; retry reloads only that tab. Only opening from the list creates or reuses a tab. If another tab already shows the next question, navigation still stays in the originating tab.
Returning to the list selects 阅读 and leaves question tabs open. Long tab names are shortened;
the full title is available in the tooltip. Native IDEA handles overflow and docking.

## Implementation

`QuestionTabs` owns stable Content instances, their current question IDs, and close/session listeners. List opening searches the current IDs; in-tab navigation updates the originating instance and ID. Async title updates are guarded by the controller request generation. Each tab gets
its own `SidebarController`, `SidebarPanel`, JCEF reader and pending request state. Controllers
share the project worker pool, but a closed tab cancels only its own requests. Content disposal
releases its reader browser/query via the Disposer tree. Account/session changes invalidate
pending work and close all question tabs, including hidden tabs. The fixed list remains.

## Follow-up network failure

The user observed the DHCP question stuck loading, followed by “题目网络请求失败”. That message
is emitted by the metadata API IOException branch, before website content parsing. Retrying
question `1811361506164535298` under a JDI probe succeeded: metadata completed in approximately
135 ms and the website answer displayed normally. The original exception was not logged, so
its precise network cause (timeout/reset/etc.) remains unknown; the successful retry does not
prove which one occurred.

The reader API now has a 15-second total call timeout. A metadata transport exception falls
back to the already verified website source, which still enforces its own explicit access state.
Thread interruption returns CANCELLED without starting the fallback. Explicit authentication,
permission and parsing failures retain their existing behavior. The website request has its
existing 30-second timeout, so the sequential request budget may reach approximately 45 seconds.
Only fixed request-stage labels and exception class names are logged, never exception messages,
credentials, request URLs or answer bodies.

## Validation

New regression tests cover failed metadata transport -> authorized page, website permission
checks, interrupted cancellation without fallback, explicit API denial without fallback, tab
reuse and close/reopen, asynchronous titles, account cleanup, independent completion, return to
list, retry, and shared worker-pool ownership. Final build and native checks are recorded below.

## Final validation (0.5.10)

- Full Gradle `check verifyPlugin buildPlugin runPluginVerifier`: BUILD SUCCESSFUL.
- 78 tests across 11 suites, zero failures/errors. Includes in-tab previous/next, current-ID list reuse, and navigation to a question already displayed in another tab.
- Plugin Verifier: compatible with IDEA Community 2025.2.6.3 (IC-252.28539.97). Four existing deprecated/scheduled-for-removal API usage warnings remain; no compatibility errors.
- Distribution: `build/distributions/Mianshiya-0.5.10.zip`.
- Restarted the native debug IDEA with the existing login/config. Its log confirms Mianshiya 0.5.10 loaded. Opened the original instant-messaging question from the list and observed correct headings, numbered list and SQL code block after the images.
- The final native next/previous click was interrupted by concurrent user input before execution. Navigation is verified by regression tests and code review, not claimed as a completed native click-through in this run.