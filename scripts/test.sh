#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p .checks
javac -encoding UTF-8 -d .checks overlay/java/com/aurora/vpn/Node.java overlay/java/com/aurora/vpn/GateParser.java overlay/java/com/aurora/vpn/FeedSnapshot.java overlay/java/com/aurora/vpn/Discovery.java tests/GateParserTest.java tests/FeedSnapshotTest.java tests/GateLiveCheck.java tests/GateSeedBuilder.java
java -cp .checks com.aurora.vpn.GateParserTest
java -cp .checks com.aurora.vpn.FeedSnapshotTest
python3 -m unittest discover -s tests -p 'test_*.py'
