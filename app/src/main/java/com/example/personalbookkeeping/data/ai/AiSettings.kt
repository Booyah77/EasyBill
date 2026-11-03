package com.example.personalbookkeeping.data.ai

import java.net.URI

const val DEFAULT_AI_URL = "https://api.deepseek.com"
const val DEFAULT_AI_MODEL = "deepseek-flash"

// Only these messages cross into the UI. Never expose transport/decoder exception text.
enum class AiFailure(val message: String) {
    NOT_CONFIGURED("请先在大模型设置中填写服务地址、模型和 API Key。"),
    INVALID_CONFIG("配置无效，请使用不含账号、查询参数或片段的 HTTPS 地址，并填写模型名称。"),
    CREDENTIAL_STORAGE("无法读取或保存 AI 凭证，请重新填写 API Key 后保存设置。"),
    INPUT("请输入账单文字，每次最多 6000 个字符。"),
    AUTH("API Key 无效或权限不足，请检查大模型设置。"),
    BALANCE("服务额度不足，请检查 API 账户余额。"),
    RATE_LIMIT("请求过于频繁或额度受限，请稍后重试。"),
    NETWORK("无法连接大模型服务，请检查网络和服务地址后重试。"),
    TIMEOUT("识别超时，请稍后重试或缩短账单文字。"),
    SERVICE("大模型服务暂时不可用，请稍后重试并检查模型名称。"),
    RESPONSE("识别结果不完整或包含无效金额、日期等内容，本次没有保存。请补全描述或缩短文字后重试。"),
    LOCAL_READ("无法读取本地分类，请稍后重试。"),
    SAVE("本次保存未完成。请重试保存同一批结果，系统会跳过已保存的相同记录。")
}

class AiException(val failure: AiFailure) : Exception(failure.message)

data class AiSettings(
    val serviceUrl: String = DEFAULT_AI_URL,
    val model: String = DEFAULT_AI_MODEL
) {
    val completionUrl: URI = normalizeCompletionUrl(serviceUrl)
    val isOfficialDeepSeek: Boolean
        get() = completionUrl.host.equals("api.deepseek.com", ignoreCase = true) &&
            completionUrl.port in listOf(-1, 443)

    init {
        if (model.isBlank() || model.length > 200 || model.any(Char::isISOControl)) {
            throw AiException(AiFailure.INVALID_CONFIG)
        }
    }
}

// Credentials deliberately have no generated toString/copy methods.
class AiCredentials(val settings: AiSettings, val apiKey: String) {
    init {
        if (apiKey.isBlank() || apiKey.length > 4096 || apiKey.any { it <= ' ' || it == '\u007f' }) {
            throw AiException(AiFailure.INVALID_CONFIG)
        }
    }
}

interface AiConfiguration {
    fun loadSettings(): AiSettings?
    fun loadCredentials(): AiCredentials?
}

private fun normalizeCompletionUrl(value: String): URI {
    try {
        if (value.length > 2048) throw AiException(AiFailure.INVALID_CONFIG)
        val uri = URI(value.trim())
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            uri.port !in -1..65535 || uri.port == 0 || uri.rawPath.orEmpty().contains('%')
        ) throw AiException(AiFailure.INVALID_CONFIG)
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.split('/').any { it == "." || it == ".." }) throw AiException(AiFailure.INVALID_CONFIG)
        val endpointPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
        return URI("https", null, uri.host.lowercase(), uri.port, endpointPath, null, null)
    } catch (exception: AiException) {
        throw exception
    } catch (exception: Exception) {
        throw AiException(AiFailure.INVALID_CONFIG)
    }
}
