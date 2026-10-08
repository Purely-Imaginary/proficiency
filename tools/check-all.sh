#!/bin/bash
# Builds the three jars, runs every unit test, then the three GameTest suites one after another.
# Run it in the build copy: tools/check-all.sh   Exit 0 means everything passed.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-$HOME/.local/java/jdk-21.0.12.1+1}
LOG=${LOG:-/tmp/proficiency-check}
FAIL=0

run() {  # name, gradle tasks...
    local name=$1; shift
    ./gradlew --no-daemon "$@" < /dev/null > "$LOG-$name.log" 2>&1
    local rc=$?
    echo "$name: gradle rc=$rc (log $LOG-$name.log)"
    [ $rc = 0 ] || FAIL=1
}

count() {  # project
    cat "$1"/build/test-results/test/*.xml 2>/dev/null \
        | grep -ohE '<testsuite [^>]*' | grep -ohE 'tests="[0-9]+" skipped="[0-9]+" failures="[0-9]+" errors="[0-9]+"' \
        | awk -F'"' -v p="$1" '{t+=$2;f+=$6;e+=$8} END {printf "  %-13s unit tests %d, failed %d, errors %d\n", p, t, f, e; exit (f+e>0)}' \
        || FAIL=1
}

run build build
for p in core common forge-1.20.1; do count $p; done
run neoforge-gametest :neoforge:runGameTestServer
run fabric-gametest :fabric:runGametest
run forge-gametest :forge-1.20.1:runGameTestServer
for name in neoforge-gametest fabric-gametest forge-gametest; do
    line=$(grep -aoE 'All [0-9]+ required tests passed|[0-9]+ required tests failed' "$LOG-$name.log" | tail -1)
    echo "  $name: ${line:-no result line}"
    case $line in "All "*) ;; *) FAIL=1;; esac
done
sha1sum neoforge/build/libs/*.jar fabric/build/libs/*.jar forge-1.20.1/build/libs/*.jar 2>/dev/null
[ $FAIL = 0 ] && echo "ALL PASSED" || echo "FAILED"
exit $FAIL
