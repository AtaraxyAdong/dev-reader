# dev-reader master integration

Checked on 2026-10-23 against AtaraxyAdong/dev-reader. No open or historical PRs were returned for this fork. No remote main branch exists.

| Branch / baseline | Disposition | Evidence |
| --- | --- | --- |
| master, 7354f4859b7425338dfcff6410e9903bca0ec0dd | Retained as integration first parent | Historical Web source has independent Git ancestry. Files retained; README replaced as requested, ignore rules combined, original MIT license preserved as LICENSE-web. |
| codex/sidebar-markdown-reader, 7c814dddf9c75050009c44377896ac9f8f7ee69a | Included through descendant UI branch | Full history includes adaptive sidebar Markdown (3613099), detail query/login state fix (65171ae), configurable fonts/window title, image windows and package validation (7c814dd). Earlier original plugin fixes remain reachable. |
| codex/mianshiya-quiet-ui, f52ecf8498976186c444fd771768de6ef97834ff | Merged as second parent | Compact toolbar, Enter activation, new identity, README and Actions. Includes packaging-cycle correction. CI 39972281000 passed before integration. |
| codex/dev-reader-master-integration | Integration branch | Starts at latest remote master; normal unrelated-history merge retains both histories, no force push or branch deletion. |

No other local or remote feature branches were present. The original local master checkout was not reset, switched or overwritten. No discarded experiment branch was found.

Superseded behavior: the older wrapping toolbar and right-button double-click opening are replaced by the quiet toolbar and intentional list activation. Old marketing README is replaced by the actual dev-reader build/install/use/limits documentation. Original account logic, API endpoints, Markdown and image-window implementations remain.

Plugin identity: io.github.ataraxyadong.devreader. The new identity does not import old login/settings/keymap data. Java package and service names remain original; integrated distributions should prevent simultaneous enablement of either old or new independent plugin.

The merged master is validated by the Build plugin ZIP workflow; Releases use successful CI ZIPs only.
