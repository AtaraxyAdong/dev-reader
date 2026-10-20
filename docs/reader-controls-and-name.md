# Reading controls and configurable name (0.5.12)

## Usage

Gear > Reading font size opens a small popup containing a 10–28px slider, editable numeric field and reset button. Dragging and valid integer entry apply immediately; both controls stay synchronized. Empty, non-integer, overflow and out-of-range input does not alter the saved value. Enter or focus loss restores the effective value. Reset follows the IDEA UI default. Existing question tabs use the same persistent setting.

Settings > Tools > 阅读设置 provides an editable tool-window name. Apply changes all initialized project windows immediately; future windows use the saved value. Blank names fall back to 阅读. The gear menu also links directly to this settings page.

The Plugins manager uses package metadata, so its name is fixed as Reading. It is separate from the configurable tool-window label. Internal plugin/tool-window IDs remain unchanged to preserve existing installations and shortcuts.

## Implementation

ReaderFontSizePopup guards slider/document feedback to avoid editing a Swing Document inside its own notification. Font changes reuse the existing CSS update path without requesting questions again. ReaderSettingsConfigurable implements normal IDEA Apply/Reset semantics. Tool-window titles subscribe during factory init with the window Disposable, covering even windows whose content has not yet been opened.
## Verification

Full check, verifyPlugin, buildPlugin and runPluginVerifier passed. 78 tests, zero failures/errors; compatible with IC-252.28539.97 with the same four deprecated API warnings. Native IDEA verified typed 20 syncing the slider, dragging back to 15 syncing the numeric field, rejecting 99 and restoring 15 on Enter. The settings shortcut opened Tools > 阅读设置; applying a custom name changed the visible title immediately, and clearing it restored 阅读. Distribution: build/distributions/Mianshiya-0.5.12.zip.
