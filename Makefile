.PHONY: all run dbg clean gradle

all:
	./gradlew -q assembleRelease

run:
	./gradlew -q assembleDebug

dbg: run
	adb install -r build/outputs/apk/debug/biikuta-debug.apk
	adb shell am start de.emdete.biikuta/.DeviceControlActivity

clean:
	./gradlew -q clean

