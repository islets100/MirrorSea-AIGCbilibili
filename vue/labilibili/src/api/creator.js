import axios from 'axios'

const chatBase = '/chat/creator/'

const creatorAxios = axios.create({
    timeout: 120000
})

creatorAxios.interceptors.response.use(
    (response) => response.data,
    (error) => Promise.reject(error)
)

/**
 * 创作辅助专用请求：返回完整 { code, msg, data }，业务码非 200 时抛出。
 */
export async function creatorRequest(config) {
    const res = await creatorAxios(config)
    if (res.code !== 200) {
        const err = new Error(res.msg || '请求失败')
        err.code = res.code
        err.msg = res.msg
        throw err
    }
    return res.data
}

export const submitCreatorSuggest = (data) =>
    creatorRequest({ method: 'post', url: chatBase + 'suggest', data })

export const getCreatorSuggestTask = (taskId, userId) =>
    creatorRequest({
        method: 'get',
        url: chatBase + `suggest/${taskId}`,
        params: { userId }
    })

export const getCreatorReferences = (taskId, userId) =>
    creatorRequest({
        method: 'get',
        url: chatBase + `suggest/${taskId}/references`,
        params: { userId }
    })

export const adoptCreatorSuggest = (taskId, data) =>
    creatorRequest({ method: 'post', url: chatBase + `suggest/${taskId}/adopt`, data })

/** 业务码对应用户提示（与 api-design §6 一致） */
export const CREATOR_ERROR_MESSAGES = {
    40001: '请先完成视频上传',
    40002: '未能识别语音，已基于草稿生成',
    40003: '未找到相似案例，已直接生成',
    42901: '排队中，请稍候',
    50001: '生成失败，请重试',
    50002: '语音识别暂不可用',
    50003: '检索服务异常',
    40301: '无权访问该任务',
    40401: '任务不存在'
}
