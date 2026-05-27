<template>
    <div class="creator-suggest-panel">
        <div class="panel-header">
            <span class="panel-title">AI 创作助手</span>
            <el-button type="primary" size="small" :loading="loading" :disabled="!canGenerate" @click="startSuggest">
                {{ loading ? '生成中...' : 'AI 生成标题/简介' }}
            </el-button>
        </div>

        <el-progress v-if="loading && progress > 0" :percentage="progress" :stroke-width="6" style="margin-top: 12px;" />

        <div v-if="streamTitle || streamIntro" class="stream-box">
            <p class="stream-label">生成过程</p>
            <div v-if="streamTitle" class="stream-field">
                <span class="field-tag">标题</span>
                <pre class="stream-content">{{ streamTitle }}</pre>
            </div>
            <div v-if="streamIntro" class="stream-field">
                <span class="field-tag">简介</span>
                <pre class="stream-content">{{ streamIntro }}</pre>
            </div>
        </div>

        <div v-if="references.length" class="result-section">
            <p class="section-title">参考来源（独立拉取）</p>
            <div v-for="(ref, idx) in references" :key="'ref-' + idx" class="ref-item">
                <el-tag size="small" :type="ref.sourceType === 'HOT_CASE' ? 'warning' : 'info'">
                    {{ ref.sourceType === 'HOT_CASE' ? '爆款案例' : '知识库' }}
                </el-tag>
                <span class="ref-title">{{ ref.title }}</span>
            </div>
        </div>

        <div v-if="result" class="result-box">
            <el-alert v-if="warningCode" type="warning" :closable="false" show-icon style="margin-bottom: 12px;">
                {{ warningMessage }}
            </el-alert>
            <div class="result-section">
                <p class="section-title">标题建议</p>
                <div v-for="(title, idx) in result.titles" :key="'t' + idx" class="suggest-item">
                    <span>{{ title }}</span>
                    <el-button size="small" link type="primary" @click="adoptTitle(title)">采用</el-button>
                </div>
            </div>
            <div class="result-section">
                <p class="section-title">简介建议</p>
                <div v-for="(intro, idx) in result.intros" :key="'i' + idx" class="suggest-item intro-item">
                    <span>{{ intro }}</span>
                    <el-button size="small" link type="primary" @click="adoptIntro(intro)">采用</el-button>
                </div>
            </div>
            <div v-if="result.references && result.references.length" class="result-section">
                <p class="section-title">结果内参考来源</p>
                <div v-for="(ref, idx) in result.references" :key="'r' + idx" class="ref-item">
                    <el-tag size="small" :type="ref.sourceType === 'HOT_CASE' ? 'warning' : 'info'">
                        {{ ref.sourceType === 'HOT_CASE' ? '爆款案例' : '知识库' }}
                    </el-tag>
                    <span class="ref-title">{{ ref.title }}</span>
                    <span v-if="ref.playCount" class="ref-meta">播放 {{ ref.playCount }}</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
    submitCreatorSuggest,
    getCreatorSuggestTask,
    getCreatorReferences,
    adoptCreatorSuggest,
    CREATOR_ERROR_MESSAGES
} from '@/api/creator'

const props = defineProps({
    userId: { type: [Number, String], required: true },
    resumableIdentifier: { type: String, default: '' },
    videoUrl: { type: String, default: '' },
    draftTitle: { type: String, default: '' },
    draftIntro: { type: String, default: '' },
    tags: { type: Array, default: () => [] }
})

const emit = defineEmits(['adopt-title', 'adopt-intro'])

const loading = ref(false)
const progress = ref(0)
const streamTitle = ref('')
const streamIntro = ref('')
const result = ref(null)
const references = ref([])
const warningCode = ref(null)
const taskId = ref('')
const canGenerate = ref(false)
let ws = null
let pollTimer = null

const warningMessage = computed(() =>
    warningCode.value ? (CREATOR_ERROR_MESSAGES[warningCode.value] || '部分能力已降级') : ''
)

watch(
    () => [props.videoUrl, props.resumableIdentifier],
    () => {
        canGenerate.value = !!(props.videoUrl || props.resumableIdentifier)
    },
    { immediate: true }
)

const showBizError = (err) => {
    const code = err.code || err.response?.data?.code
    const msg = CREATOR_ERROR_MESSAGES[code] || err.msg || err.message || '操作失败'
    if (code === 40002 || code === 40003) {
        ElMessage.warning(msg)
    } else {
        ElMessage.error(msg)
    }
}

const stopPolling = () => {
    if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
    }
}

const startPolling = (id) => {
    stopPolling()
    pollTimer = setInterval(async () => {
        try {
            const task = await getCreatorSuggestTask(id, Number(props.userId))
            if (task.progress != null) {
                progress.value = task.progress
            }
            if (task.warningCode) {
                warningCode.value = task.warningCode
            }
            const terminal = ['COMPLETED', 'FAILED', 'PARTIAL']
            if (terminal.includes(task.status)) {
                stopPolling()
                loading.value = false
                if (task.status === 'FAILED') {
                    ElMessage.error('生成失败')
                    return
                }
                if (task.result) {
                    result.value = task.result
                    progress.value = 100
                }
                if (task.warningCode === 40002 || task.warningCode === 40003) {
                    ElMessage.warning(CREATOR_ERROR_MESSAGES[task.warningCode])
                }
                try {
                    const refs = await getCreatorReferences(id, Number(props.userId))
                    references.value = buildRefList(refs)
                } catch (e) {
                    console.warn('fetch references failed', e)
                }
            }
        } catch (e) {
            console.warn('poll task failed', e)
        }
    }, 2000)
}

const buildRefList = (refs) => {
    const list = []
    if (refs.hotCases) {
        refs.hotCases.forEach((h) => {
            list.push({ sourceType: 'HOT_CASE', title: h.title, playCount: h.playCount })
        })
    }
    if (refs.knowledgeChunks) {
        refs.knowledgeChunks.forEach((k) => {
            list.push({ sourceType: 'KNOWLEDGE', title: k.sourceFile || k.content?.slice(0, 40) })
        })
    }
    return list
}

const connectWebSocket = () => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        return Promise.resolve()
    }
    return new Promise((resolve, reject) => {
        ws = new WebSocket('/wschat')
        let sessionId = ''
        ws.onopen = () => resolve()
        ws.onerror = reject
        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data)
                if (data.type === 'sessionId') {
                    sessionId = data.sessionId
                    ws.send(JSON.stringify({
                        type: 'init',
                        userId: String(props.userId),
                        sessionId: sessionId
                    }))
                    return
                }
                if (data.type !== 'creator_suggest') return
                if (taskId.value && data.taskId !== taskId.value) return

                if (data.status === 0) {
                    progress.value = data.progress || progress.value
                } else if (data.status === 1 && data.content) {
                    if (data.field === 'intro') {
                        streamIntro.value += data.content
                    } else {
                        streamTitle.value += data.content
                    }
                } else if (data.status === 2 && data.result) {
                    result.value = data.result
                    loading.value = false
                    progress.value = 100
                    stopPolling()
                } else if (data.status === -1) {
                    loading.value = false
                    stopPolling()
                    const code = data.errorCode
                    const msg = CREATOR_ERROR_MESSAGES[code] || data.message || '生成失败'
                    if (code === 40002 || code === 40003) {
                        ElMessage.warning(msg)
                    } else {
                        ElMessage.error(msg)
                    }
                }
            } catch (e) {
                console.error('WS parse error', e)
            }
        }
    })
}

const subscribeTask = (id) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            type: 'creator_subscribe',
            taskId: id,
            userId: String(props.userId)
        }))
    }
}

const startSuggest = async () => {
    if (!canGenerate.value) {
        ElMessage.warning('请先完成视频上传')
        return
    }
    loading.value = true
    progress.value = 5
    streamTitle.value = ''
    streamIntro.value = ''
    result.value = null
    references.value = []
    warningCode.value = null

    try {
        await connectWebSocket()
        const res = await submitCreatorSuggest({
            resumableIdentifier: props.resumableIdentifier,
            videoUrl: props.videoUrl,
            draftTitle: props.draftTitle,
            draftIntro: props.draftIntro,
            tags: props.tags,
            userId: Number(props.userId)
        })
        taskId.value = res.taskId
        subscribeTask(res.taskId)
        startPolling(res.taskId)
        progress.value = 15
    } catch (e) {
        loading.value = false
        stopPolling()
        showBizError(e)
        console.error(e)
    }
}

const adoptTitle = async (title) => {
    emit('adopt-title', title)
    if (taskId.value) {
        try {
            await adoptCreatorSuggest(taskId.value, {
                userId: Number(props.userId),
                adoptedTitle: title,
                adoptedIntro: props.draftIntro
            })
        } catch (e) {
            showBizError(e)
            return
        }
    }
    ElMessage.success('已填入标题')
}

const adoptIntro = async (intro) => {
    emit('adopt-intro', intro)
    if (taskId.value) {
        try {
            await adoptCreatorSuggest(taskId.value, {
                userId: Number(props.userId),
                adoptedTitle: props.draftTitle,
                adoptedIntro: intro
            })
        } catch (e) {
            showBizError(e)
            return
        }
    }
    ElMessage.success('已填入简介')
}

onBeforeUnmount(() => {
    stopPolling()
    if (ws) {
        ws.close()
        ws = null
    }
})
</script>

<style scoped lang="scss">
.creator-suggest-panel {
    margin: 16px 0;
    padding: 16px;
    border: 1px solid #e4e7ed;
    border-radius: 8px;
    background: #fafafa;
}

.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
}

.panel-title {
    font-weight: 600;
    font-size: 15px;
}

.stream-box {
    margin-top: 12px;
    padding: 10px;
    background: #fff;
    border-radius: 6px;
    max-height: 160px;
    overflow: auto;
}

.stream-label {
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
}

.stream-field {
    margin-bottom: 8px;
}

.field-tag {
    font-size: 11px;
    color: #409eff;
    margin-right: 6px;
}

.stream-content {
    font-size: 13px;
    white-space: pre-wrap;
    margin: 4px 0 0;
}

.result-section {
    margin-top: 16px;
}

.section-title {
    font-weight: 600;
    margin-bottom: 8px;
    font-size: 14px;
}

.suggest-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: 8px 10px;
    background: #fff;
    border-radius: 6px;
    margin-bottom: 8px;
    font-size: 14px;
    gap: 8px;
}

.intro-item span {
    flex: 1;
    line-height: 1.5;
}

.ref-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    font-size: 13px;
}

.ref-title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.ref-meta {
    color: #909399;
    font-size: 12px;
}
</style>
