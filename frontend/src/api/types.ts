/**
 * 与后端 VO 对齐的类型定义。
 *
 * 注意序列化约定（见后端 README）：
 * - **id 类字段是字符串**（后端把 Long 序列化成字符串，防止 JS 精度丢失）
 * - **计数字段是数字**
 * - **时间是 `yyyy-MM-dd HH:mm:ss` 格式的字符串**
 *
 * 因此这里所有 id 都声明为 string，计数声明为 number —— 不要写成 number。
 */

/** 统一响应体 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
  hasNext: boolean
}

/** 用户简要信息 */
export interface UserBrief {
  id: string
  username: string
  nickname: string
  avatar: string
  bio: string
}

/** 当前登录用户信息 */
export interface UserInfo {
  id: string
  username: string
  nickname: string
  email: string
  gender: number
  birthday: string | null
  bio: string
  avatar: string
  role: number
  followingCount: number
  followerCount: number
  postCount: number
  createTime: string
}

/** 登录响应 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

/** 用户主页信息 */
export interface UserProfile {
  id: string
  username: string
  nickname: string
  gender: number
  bio: string
  avatar: string
  followingCount: number
  followerCount: number
  postCount: number
  followedByMe: boolean
  followingMe: boolean
  mutual: boolean
  createTime: string
}

/** 动态 */
export interface Post {
  id: string
  content: string
  images: string[]
  likeCount: number
  commentCount: number
  likedByMe: boolean
  mine: boolean
  author: UserBrief
  createTime: string
}

/** 评论 */
export interface Comment {
  id: string
  postId: string
  content: string
  mine: boolean
  author: UserBrief
  createTime: string
}

/** 关注 / 粉丝列表项 */
export interface UserFollow {
  id: string
  username: string
  nickname: string
  avatar: string
  bio: string
  followedByMe: boolean
  followTime: string
}

/** 用户搜索结果 */
export interface UserSearchItem {
  id: string
  username: string
  nickname: string
  avatar: string
  bio: string
  followerCount: number
  followedByMe: boolean
}

/** 关注操作结果 */
export interface FollowResult {
  following: boolean
  followerCount: number
}

/** 点赞操作结果 */
export interface LikeResult {
  liked: boolean
  likeCount: number
}

/** 会话 */
export interface Conversation {
  id: string
  partner: UserBrief
  lastMessagePreview: string
  lastMessageTime: string | null
  unreadCount: number
}

/** 私信消息 */
export interface Message {
  id: string
  conversationId: string
  senderId: string
  receiverId: string
  content: string
  mine: boolean
  isRead: boolean
  createTime: string
}

/** 通知 */
export interface Notification {
  id: string
  /** 1 关注 2 点赞 3 评论（后端保留了 4=私信，但一期不产生） */
  type: number
  actor: UserBrief
  postId: string | null
  commentId: string | null
  content: string
  isRead: boolean
  createTime: string
}

/** 通知未读数 */
export interface NotificationUnread {
  total: number
  follow: number
  like: number
  comment: number
}

/* ==================== 管理后台 ==================== */

export interface AdminStats {
  userTotal: number
  postTotal: number
  commentTotal: number
  todayNewUser: number
  todayNewPost: number
  todayNewComment: number
  pendingReportTotal: number
}

export interface AdminUser {
  id: string
  username: string
  nickname: string
  email: string
  avatar: string
  role: number
  status: number
  followingCount: number
  followerCount: number
  postCount: number
  createTime: string
}

export interface AdminPost {
  id: string
  content: string
  images: string[]
  author: UserBrief
  likeCount: number
  commentCount: number
  createTime: string
}

export interface AdminComment {
  id: string
  postId: string
  content: string
  author: UserBrief
  createTime: string
}

export interface AdminReport {
  id: string
  reporter: UserBrief
  targetType: number
  targetId: string
  targetPreview: string
  reasonType: number
  reasonDetail: string
  status: number
  handler: UserBrief | null
  handleRemark: string
  handleTime: string | null
  createTime: string
}

export interface AdminOperationLog {
  id: string
  admin: UserBrief
  operationType: string
  targetType: number
  targetId: string | null
  detail: string
  ip: string
  createTime: string
}
