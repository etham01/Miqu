import { request } from './request'

export const fileApi = {
  /**
   * 上传图片。
   *
   * 后端按**文件头魔数**判断真实类型，改后缀名的伪装文件会被拒绝；
   * 大小上限 5MB。返回的 URL 可直接用于 `<img src>`。
   */
  uploadImage: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request<{ url: string }>({
      method: 'POST',
      url: '/files/image',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000,
    })
  },
}
