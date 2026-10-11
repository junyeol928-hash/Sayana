#!/usr/bin/env bash
# 使い方: scripts/verify-exercise.sh content/.../exercises/ex1
# solution.java が全テスト合格、starter.java がコンパイル可能かつテスト失敗、を確認する(教材執筆用の補助スクリプト)
set -u
DIR="$1"
JUNIT=$(find ~/.m2/repository/org/junit/platform/junit-platform-console-standalone -name '*.jar' | head -1)
CLASS=$(python3 -c "import yaml,sys;print(yaml.safe_load(open(sys.argv[1],encoding='utf-8'))['className'])" "$DIR/exercise.yml")
run() {
  local src="$1" W
  W=$(mktemp -d)
  cp "$DIR/$src" "$W/$CLASS.java"; cp "$DIR/test.java" "$W/${CLASS}Test.java"
  if ! javac -encoding UTF-8 -cp "$JUNIT" -d "$W/out" "$W"/*.java; then echo "COMPILE_ERROR"; return 2; fi
  timeout 20 java -Xmx256m -cp "$W/out:$JUNIT" org.junit.platform.console.ConsoleLauncher execute \
     --select-class "${CLASS}Test" --disable-banner --disable-ansi-colors --details=tree
}
echo "=== solution.java (全テスト合格が必要) ==="
run solution.java; S=$?
echo "=== starter.java (コンパイル可・テスト失敗が必要) ==="
run starter.java > /tmp/starter-out.txt 2>&1; T=$?; tail -5 /tmp/starter-out.txt
if [ $S -eq 0 ] && [ $T -eq 1 ]; then echo "OK: $DIR"; else echo "NG: $DIR (solution=$S starter=$T)"; exit 1; fi
