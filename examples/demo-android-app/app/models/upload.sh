adb shell rm -r /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/
export MODEL_NAME="gemma3-1b-it-int4-t"
adb push ${PWD}/${MODEL_NAME}.task /data/local/tmp/llm/${MODEL_NAME}.task
