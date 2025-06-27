adb shell rm -r /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/
export MODEL_NAME="hammer2.1_1.5b_q8_ekv4096"
adb push ${PWD}/${MODEL_NAME}.task /data/local/tmp/llm/${MODEL_NAME}.task