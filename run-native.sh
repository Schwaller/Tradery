#!/bin/bash
export GRAALVM_HOME="$HOME/.graalvm/graalvm-jdk-21.0.5+9.1/Contents/Home"
export DYLD_LIBRARY_PATH="$GRAALVM_HOME/lib:$DYLD_LIBRARY_PATH"
exec /Users/martinschwaller/Code/Tradery/tradery-forge/build/tradery-native "$@"
