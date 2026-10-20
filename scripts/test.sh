#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p .checks
javac -encoding UTF-8 -d .checks overlay/java/com/aurora/vpn/Node.java overlay/java/com/aurora/vpn/GateParser.java overlay/java/com/aurora/vpn/FeedSnapshot.java overlay/java/com/aurora/vpn/Discovery.java overlay/java/com/aurora/vpn/BookSource.java overlay/java/com/aurora/vpn/AttemptWindow.java overlay/java/com/aurora/vpn/DiagnosticLog.java overlay/java/com/aurora/vpn/NodeHealth.java tests/BookSourceTest.java tests/NodeHealthTest.java tests/DiagnosticLogTest.java tests/AttemptWindowTest.java tests/GateParserTest.java tests/FeedSnapshotTest.java tests/NodeSearchTest.java tests/ApkSeedCheck.java tests/GateLiveCheck.java tests/GateSeedBuilder.java tests/GateHandshakeProfiles.java
java -cp .checks com.aurora.vpn.BookSourceTest
java -cp .checks com.aurora.vpn.NodeHealthTest
java -cp .checks com.aurora.vpn.DiagnosticLogTest
java -cp .checks com.aurora.vpn.AttemptWindowTest
java -cp .checks com.aurora.vpn.GateParserTest
java -cp .checks com.aurora.vpn.FeedSnapshotTest
java -cp .checks com.aurora.vpn.NodeSearchTest
python3 -m unittest discover -s tests -p 'test_*.py'

