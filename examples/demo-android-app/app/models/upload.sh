adb shell rm -r /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/
export MODEL_NAME="Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280"
adb push ${PWD}/${MODEL_NAME}.task /data/local/tmp/llm/${MODEL_NAME}.task