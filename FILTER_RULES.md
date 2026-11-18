# Filter rules (custom build)

Settings -> **Manage filter rules**. Messages stopped by a rule are kept in **Blocked messages**
(main menu or Settings) instead of the inbox: no conversation, no notification. From there you can
restore a message to the inbox, copy it, delete it, or create an "Allow" rule for its sender.

* **Action**: *Block* or *Allow (never block)*. Allow rules are checked first and also override the
  old blocked numbers / keywords / "block unknown numbers".
* **Sender condition** (optional): one pattern per line or comma separated. `*` matches anything.
  Numbers (`+1456*`, `+98912*`) and sender names (`google`, `*bank*`) work, case-insensitive.
  Or switch to regular expressions.
* **Text condition** (optional): words/phrases that contain ANY (OR) or ALL (AND), or regular
  expressions (Java syntax, partial match, dot matches line breaks). Persian/Arabic digits and
  yeh/kaf variants are treated as equal.
* If both conditions are set, both must match.
* **Test rules** (menu in the rules screen) shows what your rules would do with a sample message.

Notes: MMS stopped by a rule are dropped (not stored in Blocked messages). Old keyword/number
blocking keeps working as before. Requires Android 8+ (Fossify Messages minSdk 26).
