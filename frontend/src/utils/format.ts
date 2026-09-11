/** 后端统一返回 `yyyy-MM-dd HH:mm:ss`。 */
export function parseTime(value: string | null | undefined): Date | null {
  if (!value) return null
  // iOS Safari 不接受 `yyyy-MM-dd HH:mm:ss`，必须换成 ISO 的 `yyyy/MM/dd HH:mm:ss`
  const normalized = value.replace(/-/g, '/')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 2 天前 / 具体日期。 */
export function relativeTime(value: string | null | undefined): string {
  const date = parseTime(value)
  if (!date) return ''

  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `${Math.floor(diff / day)} 天前`

  return date.toLocaleDateString('zh-CN')
}

/** 聊天场景需要更细的时间：今天显示时分，昨天显示"昨天 时:分"，更早显示日期。 */
export function chatTime(value: string | null | undefined): string {
  const date = parseTime(value)
  if (!date) return ''

  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (sameDay) return time

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return `昨天 ${time}`

  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

/** 会话列表用的时间：今天只显示时分，更早显示日期。 */
export function conversationTime(value: string | null | undefined): string {
  const date = parseTime(value)
  if (!date) return ''
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}/${date.getDate()}`
  }
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

/** 后端把 id 序列化成字符串，这里统一转成数字用于展示兜底。 */
export function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

export const GENDER_LABELS: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女',
}

export const REPORT_REASON_LABELS: Record<number, string> = {
  1: '垃圾广告',
  2: '辱骂骚扰',
  3: '色情低俗',
  4: '违法违规',
  5: '其他',
}

export const REPORT_STATUS_LABELS: Record<number, string> = {
  0: '待处理',
  1: '已处理',
  2: '已驳回',
}

export const TARGET_TYPE_LABELS: Record<number, string> = {
  1: '用户',
  2: '动态',
  3: '评论',
  4: '举报',
}

export const NOTIFICATION_TYPE_LABELS: Record<number, string> = {
  1: '关注',
  2: '点赞',
  3: '评论',
}

/** 把一段文本截断，用于列表预览。 */
export function truncate(text: string, max: number): string {
  if (!text) return ''
  return text.length <= max ? text : `${text.slice(0, max)}…`
}

/** 生成头像占位：没有头像时用昵称首字生成一个带底色的圆形。 */
export function avatarText(nickname: string): string {
  if (!nickname) return '?'
  return nickname.slice(0, 1).toUpperCase()
}
