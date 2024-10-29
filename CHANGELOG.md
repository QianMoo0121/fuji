> The version number of fuji follows `semver` now: https://semver.org/ 
 
cherry-pick commits from `fuji v4.2.1`:

- docs: simplify the docs
  - use a fancy header to reflect the structure of the document. 
  - add the `listing of commands`.
  - remove the usage of levels that deepen than h6.
  - box the code fence.
- i18n: translation fix for `id_ID` and `zh_TW`. (contributor: @yichifauzi)
- fix: ensure the commands are executes in main thread. (command_meta.delay module)
- fix: the lore of meta-data doesn't show in `/fuji inspect fuji-commands` gui. (fuji module)

- fix: possible to occur `Not a JSON Object: null` when a new fake-player is spawned via `carpet` mod. (placeholder module)