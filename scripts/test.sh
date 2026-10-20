#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
mkdir -p .checks
javac -encoding UTF-8 -d .checks overlay/java/com/aurora/vpn/Node.java overlay/java/com/aurora/vpn/GateParser.java tests/GateParserTest.java
java -cp .checks com.aurora.vpn.GateParserTest
python3 -m unittest discover -s tests -p 'test_*.py'
