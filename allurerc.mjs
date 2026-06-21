// Allure 3 report config. Deliberately no `import { defineConfig } from "allure"`: the repo root is
// not a Node package, so the publish_test_report job runs the generator via `npx allure` with no
// local node_modules — a bare import of "allure" would fail to resolve there. defineConfig is only
// a typing helper; a plain object is all the generator reads.
//
// historyPath is the single accumulating JSONL file backing the report's trends. The CI job
// (.github/workflows/gradle-build.yml) restores the previous run's copy here before `allure
// generate` and persists the rewritten file back to gh-pages afterward. It must stay at a stable
// path — never inside a per-run report folder, or every run would start from empty history.
export default {
  name: "Kinotic Test Report",
  historyPath: "./history.jsonl",
};
