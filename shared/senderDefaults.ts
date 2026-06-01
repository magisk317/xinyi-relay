import type { SnapshotSender } from './contracts/console'
import senderSchemaContract from './contracts/senderSchemas.json'
import {
  normalizeSenderActiveSchedule
} from './senderActiveSchedule'

export type SenderUiLocale = 'en' | 'zh-CN' | 'zh-TW'
export type SenderFieldKind = 'text' | 'textarea' | 'number' | 'boolean' | 'select' | 'json'
export type SenderSettingContractFieldType =
  | 'TEXT'
  | 'SECRET'
  | 'BOOLEAN'
  | 'INTEGER'
  | 'STRING_MAP'
  | 'EMAIL_RECIPIENTS'
  | 'PROXY_TYPE'

type JsonRecord = Record<string, unknown>
type LocalizedText = {
  en: string
  'zh-CN': string
  'zh-TW'?: string
}

export type SenderFieldSchema = {
  key: string
  kind: SenderFieldKind
  label: LocalizedText
  placeholder?: LocalizedText
  rows?: number
  fullWidth?: boolean
  showIf?: { field: string; equals: string }
  options?: ReadonlyArray<{
    value: string
    label: LocalizedText
  }>
}

export type SenderSettingContractField = {
  name: string
  type: SenderSettingContractFieldType
  aliases: string[]
  requiredForEnable: boolean
  defaultValue?: string
  options?: ReadonlyArray<{
    value: string
  }>
}

export type SenderSettingSchemaContract = {
  senderType: number
  fields: SenderSettingContractField[]
}

const SENDER_SCHEMA_CONTRACTS = senderSchemaContract as SenderSettingSchemaContract[]
const SENDER_SCHEMA_CONTRACT_BY_TYPE = new Map(
  SENDER_SCHEMA_CONTRACTS.map((schema) => [schema.senderType, schema])
)

const DEFAULT_SENDER_SETTING_OVERRIDES: Record<number, JsonRecord> = {
  1: { encryptionProtocol: 'Plain' },
  2: { level: 'active' },
  15: { uriType: 'tcp' },
}

const DEFAULT_SENDER_SETTINGS: Record<number, JsonRecord> = Object.fromEntries(
  SENDER_SCHEMA_CONTRACTS.map((schema) => [
    schema.senderType,
    {
      ...Object.fromEntries(
        schema.fields.map((field) => [field.name, defaultValueForContractField(field)])
      ),
      ...(DEFAULT_SENDER_SETTING_OVERRIDES[schema.senderType] ?? {})
    }
  ])
)

const PROXY_OPTIONS = [
  { value: 'DIRECT', label: { en: 'Direct', 'zh-CN': '直连', 'zh-TW': '直連' } },
  { value: 'HTTP', label: { en: 'HTTP proxy', 'zh-CN': 'HTTP 代理', 'zh-TW': 'HTTP 代理' } },
  { value: 'SOCKS', label: { en: 'SOCKS proxy', 'zh-CN': 'SOCKS 代理', 'zh-TW': 'SOCKS 代理' } },
] as const

const FLAG_FIELDS = ['status', 'receiveCode', 'receiveNonCode', 'receiveAppNotify', 'receiveCallNotify'] as const

const SENDER_FIELD_SCHEMAS: Record<number, SenderFieldSchema[]> = {
  0: [
    field('token', 'text', 'Token', 'Token'),
    field('secret', 'text', '签名 Secret', 'Signing secret'),
    field('msgtype', 'text', '消息类型', 'Message type'),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
    field('atAll', 'boolean', '艾特所有人', 'Mention all'),
    field('atMobiles', 'textarea', '艾特手机号', 'Mention mobiles', { rows: 3 }),
    field('atDingtalkIds', 'textarea', '艾特 DingTalk ID', 'Mention DingTalk IDs', { rows: 3 }),
  ],
  1: [
    field('mailType', 'text', '邮件类型', 'Mail type'),
    field('authEmail', 'text', '登录邮箱', 'Authentication email'),
    field('fromEmail', 'text', '显示发件邮箱', 'Visible from email'),
    field('pwd', 'text', '邮箱密码', 'Password'),
    field('host', 'text', 'SMTP 主机', 'SMTP host'),
    field('port', 'text', 'SMTP 端口', 'SMTP port'),
    field('ssl', 'boolean', '启用 SSL', 'Enable SSL'),
    field('startTls', 'boolean', '启用 STARTTLS', 'Enable STARTTLS'),
    field('title', 'text', '邮件标题', 'Email title'),
    field('toEmail', 'text', '收件邮箱', 'Recipient email'),
    field('fromEmailAlias', 'text', '显示发件人名称', 'Visible sender name'),
    field('encryptionProtocol', 'text', '加密协议', 'Encryption protocol'),
    field('keystore', 'textarea', '证书内容', 'Keystore / certificate', { rows: 3 }),
    field('password', 'text', '证书密码', 'Certificate password'),
    field('recipients', 'json', '收件人映射 JSON', 'Recipients JSON', { rows: 5, fullWidth: true }),
  ],
  2: [
    field('server', 'text', 'Bark 地址', 'Bark server'),
    field('group', 'text', '分组', 'Group'),
    field('icon', 'text', '图标 URL', 'Icon URL'),
    field('sound', 'text', '铃声', 'Sound'),
    field('badge', 'text', '角标', 'Badge'),
    field('url', 'text', '跳转链接', 'Open URL'),
    field('level', 'text', '通知级别', 'Level'),
    field('title', 'text', '标题模板', 'Title template'),
    field('transformation', 'text', '加密方式', 'Transformation'),
    field('key', 'text', '加密密钥', 'Encryption key'),
    field('iv', 'text', 'IV', 'IV'),
    field('call', 'text', '持续提醒', 'Call'),
    field('autoCopy', 'text', '自动复制', 'Auto copy'),
  ],
  3: [
    field('method', 'text', '请求方法', 'HTTP method'),
    field('webServer', 'text', 'Webhook 地址', 'Webhook URL'),
    field('secret', 'text', '签名密钥', 'Secret'),
    field('response', 'text', '成功响应关键字', 'Success response keyword'),
    field('proxyType', 'select', '代理类型', 'Proxy type', { options: PROXY_OPTIONS }),
    field('proxyHost', 'text', '代理主机', 'Proxy host'),
    field('proxyPort', 'text', '代理端口', 'Proxy port'),
    field('proxyAuthenticator', 'boolean', '代理鉴权', 'Proxy auth'),
    field('proxyUsername', 'text', '代理用户名', 'Proxy username'),
    field('proxyPassword', 'text', '代理密码', 'Proxy password'),
    field('webParams', 'textarea', '请求参数', 'Request params', { rows: 4, fullWidth: true }),
    field('headers', 'json', '请求头 JSON', 'Headers JSON', { rows: 4, fullWidth: true }),
  ],
  4: [
    field('webHook', 'text', '机器人 WebHook', 'Robot WebHook'),
    field('msgType', 'text', '消息类型', 'Message type'),
    field('atAll', 'boolean', '艾特所有人', 'Mention all'),
    field('atUserIds', 'textarea', '艾特成员 ID', 'Mention user IDs', { rows: 3 }),
    field('atMobiles', 'textarea', '艾特手机号', 'Mention mobiles', { rows: 3 }),
  ],
  5: [
    field('corpID', 'text', '企业 ID', 'Corp ID'),
    field('agentID', 'text', '应用 Agent ID', 'Agent ID'),
    field('secret', 'text', '应用 Secret', 'App secret'),
    field('toUser', 'text', '接收用户', 'To user'),
    field('toParty', 'textarea', '接收部门', 'To party', { rows: 2 }),
    field('toTag', 'textarea', '接收标签', 'To tag', { rows: 2 }),
    field('customizeAPI', 'text', 'API 地址', 'Custom API'),
    field('atAll', 'boolean', '艾特所有人', 'Mention all'),
    field('proxyType', 'select', '代理类型', 'Proxy type', { options: PROXY_OPTIONS }),
    field('proxyHost', 'text', '代理主机', 'Proxy host'),
    field('proxyPort', 'text', '代理端口', 'Proxy port'),
    field('proxyAuthenticator', 'boolean', '代理鉴权', 'Proxy auth'),
    field('proxyUsername', 'text', '代理用户名', 'Proxy username'),
    field('proxyPassword', 'text', '代理密码', 'Proxy password'),
  ],
  6: [
    field('sendKey', 'text', 'SendKey', 'SendKey'),
    field('channel', 'text', 'Channel', 'Channel'),
    field('openid', 'text', 'OpenID', 'OpenID'),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
  ],
  7: [
    field('method', 'text', '请求方法', 'HTTP method'),
    field('apiBase', 'text', 'API 地址', 'API base'),
    field('apiToken', 'text', 'Bot Token', 'Bot token'),
    field('chatId', 'text', 'Chat ID', 'Chat ID'),
    field('messageThreadId', 'text', '话题 ID', 'Message thread ID'),
    field('parseMode', 'text', '解析模式', 'Parse mode'),
    field('proxyType', 'select', '代理类型', 'Proxy type', { options: PROXY_OPTIONS }),
    field('proxyHost', 'text', '代理主机', 'Proxy host'),
    field('proxyPort', 'text', '代理端口', 'Proxy port'),
    field('proxyAuthenticator', 'boolean', '代理鉴权', 'Proxy auth'),
    field('proxyUsername', 'text', '代理用户名', 'Proxy username'),
    field('proxyPassword', 'text', '代理密码', 'Proxy password'),
  ],
  8: [
    field('simSlot', 'number', 'SIM 卡槽', 'SIM slot'),
    field('mobiles', 'textarea', '目标号码', 'Target numbers', { rows: 3 }),
    field('onlyNoNetwork', 'boolean', '仅无网络时发送', 'Only when no network'),
  ],
  9: [
    field('webhook', 'text', 'Webhook 地址', 'Webhook URL'),
    field('secret', 'text', '签名密钥', 'Secret'),
    field('msgType', 'text', '消息类型', 'Message type'),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
    field('messageCard', 'textarea', '消息卡片 JSON', 'Message card JSON', { rows: 5, fullWidth: true }),
  ],
  10: [
    field('website', 'text', 'PushPlus 域名', 'PushPlus host'),
    field('token', 'text', 'Token', 'Token'),
    field('topic', 'text', 'Topic', 'Topic'),
    field('template', 'text', '模板', 'Template'),
    field('channel', 'text', 'Channel', 'Channel'),
    field('webhook', 'text', 'Webhook', 'Webhook'),
    field('callbackUrl', 'text', '回调地址', 'Callback URL'),
    field('validTime', 'text', '有效期', 'Valid time'),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
  ],
  11: [
    field('webServer', 'text', 'Gotify 地址', 'Gotify URL'),
    field('title', 'text', '标题', 'Title'),
    field('priority', 'text', '优先级', 'Priority'),
  ],
  12: [
    field('agentID', 'text', 'Agent ID', 'Agent ID'),
    field('appKey', 'text', 'App Key', 'App key'),
    field('appSecret', 'text', 'App Secret', 'App secret'),
    field('userIds', 'textarea', '接收用户 ID', 'User IDs', { rows: 3 }),
    field('msgKey', 'text', '消息 Key', 'Message key'),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
    field('proxyType', 'select', '代理类型', 'Proxy type', { options: PROXY_OPTIONS }),
    field('proxyHost', 'text', '代理主机', 'Proxy host'),
    field('proxyPort', 'text', '代理端口', 'Proxy port'),
    field('proxyAuthenticator', 'boolean', '代理鉴权', 'Proxy auth'),
    field('proxyUsername', 'text', '代理用户名', 'Proxy username'),
    field('proxyPassword', 'text', '代理密码', 'Proxy password'),
  ],
  13: [
    field('authType', 'select', '鉴权方式', 'Auth type', {
      options: [
        { value: 'app_id', label: { en: 'App ID / Secret', 'zh-CN': 'App ID / Secret' } },
        { value: 'token', label: { en: 'Bot Token', 'zh-CN': 'Bot Token' } },
      ],
    }),
    field('appId', 'text', 'App ID', 'App ID', { showIf: { field: 'authType', equals: 'app_id' } }),
    field('appSecret', 'text', 'App Secret', 'App secret', { showIf: { field: 'authType', equals: 'app_id' } }),
    field('botToken', 'text', 'Bot Token', 'Bot token', { showIf: { field: 'authType', equals: 'token' } }),
    field('receiveId', 'text', '接收 ID', 'Receive ID'),
    field('receiveIdType', 'select', '接收 ID 类型', 'Receive ID type', {
      options: [
        { value: 'user_id', label: { en: 'User ID', 'zh-CN': '用户 ID' } },
        { value: 'open_id', label: { en: 'Open ID', 'zh-CN': '开放 ID' } },
        { value: 'union_id', label: { en: 'Union ID', 'zh-CN': '统一 ID' } },
        { value: 'email', label: { en: 'Email', 'zh-CN': '邮箱' } },
        { value: 'chat_id', label: { en: 'Chat ID', 'zh-CN': '会话 ID' } },
      ],
    }),
    field('msgType', 'select', '消息类型', 'Message type', {
      options: [
        { value: 'interactive', label: { en: 'Interactive', 'zh-CN': '交互卡片' } },
        { value: 'text', label: { en: 'Text', 'zh-CN': '文本' } },
      ],
    }),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
    field('messageCard', 'textarea', '消息卡片 JSON', 'Message card JSON', { rows: 5, fullWidth: true }),
  ],
  14: [
    field('urlScheme', 'textarea', 'URL Scheme', 'URL scheme', { rows: 4, fullWidth: true }),
  ],
  15: [
    field('method', 'text', '协议类型', 'Method'),
    field('address', 'text', '地址', 'Address'),
    field('port', 'number', '端口', 'Port'),
    field('msgTemplate', 'textarea', '消息模板', 'Message template', { rows: 3, fullWidth: true }),
    field('secret', 'text', '签名密钥', 'Secret'),
    field('response', 'text', '成功响应关键字', 'Success response'),
    field('username', 'text', '用户名', 'Username'),
    field('password', 'text', '密码', 'Password'),
    field('inCharset', 'text', '输入编码', 'Input charset'),
    field('outCharset', 'text', '输出编码', 'Output charset'),
    field('inMessageTopic', 'text', '订阅主题', 'Inbound topic'),
    field('outMessageTopic', 'text', '发布主题', 'Outbound topic'),
    field('uriType', 'text', 'URI 类型', 'URI type'),
    field('path', 'text', '路径', 'Path'),
    field('clientId', 'text', '客户端 ID', 'Client ID'),
    field('qos', 'number', 'QoS', 'QoS'),
    field('retained', 'boolean', '保留消息', 'Retained'),
  ],
  16: [
    field('server', 'text', 'Ntfy 地址', 'Ntfy server'),
    field('topic', 'text', 'Topic', 'Topic'),
    field('token', 'text', 'Token', 'Token'),
    field('title', 'text', '标题', 'Title'),
    field('priority', 'text', '优先级', 'Priority'),
    field('tags', 'text', '标签', 'Tags'),
  ],
  17: [
    field('token', 'text', 'Token', 'Token'),
    field('recvId', 'text', '接收者 ID', 'Recipient ID'),
    field('recvType', 'select', '接收者类型', 'Recipient type', {
      options: [
        { value: 'user', label: { en: 'User', 'zh-CN': '用户', 'zh-TW': '用戶' } },
        { value: 'group', label: { en: 'Group', 'zh-CN': '群组', 'zh-TW': '群組' } },
      ],
    }),
    field('contentType', 'select', '消息类型', 'Message type', {
      options: [
        { value: 'text', label: { en: 'Text', 'zh-CN': '文本', 'zh-TW': '文字' } },
        { value: 'markdown', label: { en: 'Markdown', 'zh-CN': 'Markdown', 'zh-TW': 'Markdown' } },
      ],
    }),
    field('titleTemplate', 'text', '标题模板', 'Title template'),
  ],
}

export function buildSenderDraftJson(type: number): string {
  const normalized = normalizeSenderJson(type, '')
  if (!normalized) return ''
  return JSON.stringify(JSON.parse(normalized), null, 2)
}

export function prettySenderJson(type: number, rawJson: string): string {
  const normalized = normalizeSenderJson(type, rawJson)
  if (!normalized) return ''
  return JSON.stringify(JSON.parse(normalized), null, 2)
}

export function nextSenderId(senders: SnapshotSender[]): number {
  const maxId = senders.reduce((current, sender) => {
    const next = Number.isFinite(sender.id) ? Math.trunc(sender.id) : 0
    return next > current ? next : current
  }, 0)
  return maxId + 1
}

export function resolveSenderJsonForTypeChange(
  currentType: number,
  nextType: number,
  currentJson: string,
): string {
  if (currentType === nextType) {
    return currentJson
  }

  const trimmedCurrentJson = currentJson.trim()
  if (!trimmedCurrentJson) {
    return buildSenderDraftJson(nextType)
  }

  const currentDefaultJson = canonicalizeJsonObject(buildSenderDraftJson(currentType))
  const normalizedCurrentJson = canonicalizeJsonObject(currentJson)
  return normalizedCurrentJson != null && normalizedCurrentJson === currentDefaultJson
    ? buildSenderDraftJson(nextType)
    : currentJson
}

export function normalizeSnapshotSender(sender: SnapshotSender): SnapshotSender {
  const normalized: SnapshotSender = {
    ...sender,
    id: Number.isFinite(sender.id) && sender.id > 0 ? Math.trunc(sender.id) : 0,
    type: Number.isFinite(sender.type) ? Math.trunc(sender.type) : 0,
    name: sender.name.trim(),
    jsonSetting: sender.jsonSetting.trim(),
    activeSchedule: normalizeSenderActiveSchedule(sender.activeSchedule),
  }

  for (const field of FLAG_FIELDS) {
    normalized[field] = normalized[field] === 1 ? 1 : 0
  }

  return normalized
}

export function getSenderFieldSchemas(type: number): SenderFieldSchema[] {
  const contractFields = getSenderSettingContractFields(type)
  return (SENDER_FIELD_SCHEMAS[type] ?? []).map((field) => {
    const contractField = contractFields.find((item) => item.name === field.key)
    if (!contractField?.options?.length) return field

    const existingLabels = new Map((field.options ?? []).map((option) => [option.value, option.label]))
    return {
      ...field,
      kind: 'select',
      options: contractField.options.map((option) => ({
        value: option.value,
        label: existingLabels.get(option.value) ?? {
          en: option.value,
          'zh-CN': option.value,
          'zh-TW': option.value,
        }
      }))
    }
  })
}

export function getSenderSettingSchemaContracts(): SenderSettingSchemaContract[] {
  return SENDER_SCHEMA_CONTRACTS
}

export function getSenderSettingContractFields(type: number): SenderSettingContractField[] {
  return SENDER_SCHEMA_CONTRACT_BY_TYPE.get(type)?.fields ?? []
}

export function parseSenderFormState(type: number, rawJson: string): JsonRecord {
  const normalizedJson = normalizeSenderJson(type, rawJson)
  const parsed = parseJsonObject(normalizedJson) ?? {}
  const fields = getSenderFieldSchemas(type)
  const formState: JsonRecord = {}
  for (const field of fields) {
    const value = parsed[field.key]
    formState[field.key] = field.kind === 'json'
      ? JSON.stringify(isPlainObject(value) ? value : {}, null, 2)
      : value
  }
  if (type === 1) {
    const authEmail = typeof formState.authEmail === 'string' ? formState.authEmail : ''
    const fromEmail = typeof formState.fromEmail === 'string' ? formState.fromEmail : ''
    const alias = typeof formState.fromEmailAlias === 'string' ? formState.fromEmailAlias : ''
    const nickname = typeof parsed.nickname === 'string' ? parsed.nickname : ''
    formState.authEmail = authEmail || fromEmail
    formState.fromEmailAlias = alias || nickname
  }
  return formState
}

export function buildSenderJsonFromFormState(type: number, formState: JsonRecord): string {
  const defaults = DEFAULT_SENDER_SETTINGS[type]
  if (defaults == null) {
    return ''
  }

  const fields = getSenderFieldSchemas(type)
  const raw: JsonRecord = {}
  for (const [key, defaultValue] of Object.entries(defaults)) {
    const field = fields.find((item) => item.key === key)
    const candidate = formState[key]
    if (!field) {
      raw[key] = defaultValue
      continue
    }
    raw[key] = normalizeFormValue(field, defaultValue, candidate)
  }
  if (type === 1) {
    const authEmail = typeof raw.authEmail === 'string' ? raw.authEmail : ''
    const fromEmail = typeof raw.fromEmail === 'string' ? raw.fromEmail : ''
    const alias = typeof raw.fromEmailAlias === 'string' ? raw.fromEmailAlias : ''
    raw.authEmail = authEmail || fromEmail
    raw.nickname = alias
  }
  return JSON.stringify(sanitizeBySchema(defaults, raw))
}

export function normalizeSenderJson(type: number, rawJson: string): string {
  const defaults = DEFAULT_SENDER_SETTINGS[type]
  if (defaults == null) {
    return rawJson.trim()
  }

  const parsed = parseJsonObject(rawJson)
  const normalized = sanitizeBySchema(defaults, parsed)
  return JSON.stringify(normalized)
}

export function resolveSenderText(locale: SenderUiLocale, text: LocalizedText): string {
  if (locale === 'zh-TW') return text['zh-TW'] ?? text['zh-CN']
  if (locale === 'zh-CN') return text['zh-CN']
  return text.en
}

function field(
  key: string,
  kind: SenderFieldKind,
  zhCn: string,
  en: string,
  extra?: Partial<Omit<SenderFieldSchema, 'key' | 'kind' | 'label'>>,
): SenderFieldSchema {
  return {
    key,
    kind,
    label: {
      en,
      'zh-CN': zhCn,
      'zh-TW': zhCn,
    },
    ...extra,
  }
}

function normalizeFormValue(field: SenderFieldSchema, defaultValue: unknown, candidate: unknown): unknown {
  if (field.kind === 'boolean') {
    return candidate === true
  }
  if (field.kind === 'number') {
    if (typeof candidate === 'number' && Number.isFinite(candidate)) return candidate
    if (typeof candidate === 'string' && candidate.trim() !== '') {
      const parsed = Number(candidate)
      if (Number.isFinite(parsed)) return parsed
    }
    return defaultValue
  }
  if (field.kind === 'json') {
    if (isPlainObject(candidate)) {
      return candidate
    }
    if (typeof candidate !== 'string' || !candidate.trim()) {
      return isPlainObject(defaultValue) ? defaultValue : {}
    }
    try {
      const parsed = JSON.parse(candidate)
      return isPlainObject(parsed) ? parsed : defaultValue
    } catch {
      return defaultValue
    }
  }
  if (field.kind === 'select') {
    if (typeof candidate !== 'string') return defaultValue
    const options = field.options ?? []
    return options.length === 0 || options.some((option) => option.value === candidate) ? candidate : defaultValue
  }
  return typeof candidate === 'string' ? candidate : `${candidate ?? defaultValue ?? ''}`
}

function defaultValueForContractField(field: SenderSettingContractField): unknown {
  const defaultValue = field.defaultValue
  if (field.type === 'BOOLEAN') {
    return defaultValue === 'true'
  }
  if (field.type === 'INTEGER') {
    const parsed = Number(defaultValue)
    return Number.isFinite(parsed) ? parsed : 0
  }
  if (field.type === 'STRING_MAP' || field.type === 'EMAIL_RECIPIENTS') {
    return {}
  }
  return defaultValue ?? ''
}

function parseJsonObject(rawJson: string): JsonRecord | null {
  const trimmed = rawJson.trim()
  if (!trimmed) return null
  try {
    const parsed = JSON.parse(trimmed)
    return isPlainObject(parsed) ? parsed : null
  } catch {
    return null
  }
}

function canonicalizeJsonObject(rawJson: string): string | null {
  const parsed = parseJsonObject(rawJson)
  return parsed == null ? null : JSON.stringify(parsed)
}

function sanitizeBySchema(schema: JsonRecord, raw: JsonRecord | null): JsonRecord {
  const normalized: JsonRecord = {}
  for (const [key, defaultValue] of Object.entries(schema)) {
    const candidate = raw?.[key]
    normalized[key] = sanitizeValue(defaultValue, candidate)
  }
  if (schema === DEFAULT_SENDER_SETTINGS[1]) {
    const fromEmail = typeof normalized.fromEmail === 'string' ? normalized.fromEmail : ''
    const authEmail = typeof normalized.authEmail === 'string' ? normalized.authEmail : ''
    const rawNickname = typeof raw?.nickname === 'string' ? raw.nickname : ''
    const alias = typeof normalized.fromEmailAlias === 'string' ? normalized.fromEmailAlias : ''
    const resolvedAlias = alias || rawNickname
    normalized.authEmail = authEmail || fromEmail
    normalized.fromEmailAlias = resolvedAlias
    normalized.nickname = resolvedAlias
  }
  return normalized
}

function sanitizeValue(defaultValue: unknown, candidate: unknown): unknown {
  if (typeof defaultValue === 'string') {
    if (defaultValue === 'DIRECT') {
      return sanitizeProxyType(candidate)
    }
    return typeof candidate === 'string' ? candidate : defaultValue
  }
  if (typeof defaultValue === 'number') {
    return typeof candidate === 'number' && Number.isFinite(candidate) ? candidate : defaultValue
  }
  if (typeof defaultValue === 'boolean') {
    return typeof candidate === 'boolean' ? candidate : defaultValue
  }
  if (Array.isArray(defaultValue)) {
    return Array.isArray(candidate) ? candidate : defaultValue
  }
  if (isPlainObject(defaultValue)) {
    return isPlainObject(candidate) ? candidate : defaultValue
  }
  return defaultValue
}

function sanitizeProxyType(candidate: unknown): string {
  return candidate === 'HTTP' || candidate === 'SOCKS' ? candidate : 'DIRECT'
}

function isPlainObject(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
