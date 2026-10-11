#!/usr/bin/env bash
# 使い方: scripts/verify-exercise.sh content/.../exercises/ex1
# solution.java が全テスト合格、starter.java がコンパイル可能かつテスト失敗、を確認する(教材執筆用の補助スクリプト)
# exercise.yml に libraries: があるときは、Maven の依存一式(Spring など)をクラスパスに入れて確認する。
set -u
DIR="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JUNIT=$(find ~/.m2/repository/org/junit/platform/junit-platform-console-standalone -name '*.jar' | head -1)
read -r CLASS LIBS < <(python3 -c "
import yaml,sys
y=yaml.safe_load(open(sys.argv[1],encoding='utf-8'))
print(y['className'], ','.join(y.get('libraries') or []) or '-')" "$DIR/exercise.yml")
CP="$JUNIT"
if [ "$LIBS" != "-" ]; then
  CPFILE="${TMPDIR:-/tmp}/dojo-deps-classpath.txt"
  if [ ! -s "$CPFILE" ]; then
    (cd "$ROOT" && ./mvnw -q -B dependency:build-classpath -Dmdep.outputFile="$CPFILE" -Dmdep.includeScope=test >/dev/null)
  fi
  CP="$JUNIT:$(cat "$CPFILE")"
fi
run() {
  local src="$1" W
  W=$(mktemp -d)
  cp "$DIR/$src" "$W/$CLASS.java"; cp "$DIR/test.java" "$W/${CLASS}Test.java"
  if ! javac -encoding UTF-8 -parameters -proc:none -cp "$CP" -d "$W/out" "$W"/*.java; then echo "COMPILE_ERROR"; return 2; fi
  timeout 40 java -Xmx256m -cp "$W/out:$CP" org.junit.platform.console.ConsoleLauncher execute \
     --select-class "${CLASS}Test" --disable-banner --disable-ansi-colors --details=tree
}
echo "=== solution.java (全テスト合格が必要) ==="
run solution.java; S=$?
echo "=== starter.java (コンパイル可・テスト失敗が必要) ==="
OUT=$(mktemp)
run starter.java > "$OUT" 2>&1; T=$?; tail -5 "$OUT"; rm -f "$OUT"
if [ $S -eq 0 ] && [ $T -eq 1 ]; then echo "OK: $DIR"; else echo "NG: $DIR (solution=$S starter=$T)"; exit 1; fi
