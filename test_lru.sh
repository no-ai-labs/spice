#!/bin/bash
SUCCESS=0
FAIL=0

for i in {1..10}; do
    echo -n "Run $i: "
    RESULT=$(./gradlew :spice-core:test --tests "*CachedToolTest*LRU*" --rerun-tasks 2>&1)
    if echo "$RESULT" | grep -q "test LRU eviction() PASSED"; then
        echo "PASSED"
        SUCCESS=$((SUCCESS+1))
    else
        echo "FAILED"
        FAIL=$((FAIL+1))
    fi
done

echo ""
echo "Summary: $SUCCESS passed, $FAIL failed"
