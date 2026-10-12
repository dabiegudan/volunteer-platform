import { API_BASE_URL } from '@/utils/request'

/**
 * 文件下载工具
 * 处理 Blob/文件下载逻辑
 */

/**
 * 从URL下载文件
 * @param {string} url 下载链接
 * @param {string} filename 文件名
 */
export function downloadFromUrl(url, filename = 'download.xlsx') {
  // #ifdef H5
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // #endif

  // #ifndef H5
  uni.downloadFile({
    url,
    success: (res) => {
      if (res.statusCode === 200) {
        uni.openDocument({
          filePath: res.tempFilePath,
          showMenu: true
        })
      }
    },
    fail: (err) => {
      console.error('下载失败:', err)
      uni.showToast({ title: '下载失败', icon: 'none' })
    }
  })
  // #endif
}

/**
 * 触发文件下载
 * @param {string} url 文件URL
 * @param {string} filename 文件名
 */
export function triggerDownload(url, filename) {
  downloadFromUrl(url, filename)
}

export async function downloadAuthenticated(path, params = {}, filename = 'download.xlsx') {
  const query = new URLSearchParams(params).toString()
  const url = `${API_BASE_URL}${path}${query ? `?${query}` : ''}`
  const token = uni.getStorageSync('user_token')

  // #ifdef H5
  const response = await fetch(url, {
    headers: {
      Authorization: token ? `Bearer ${token}` : ''
    }
  })
  if (!response.ok) {
    throw new Error(`下载失败: ${response.status}`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  downloadFromUrl(objectUrl, filename)
  URL.revokeObjectURL(objectUrl)
  // #endif

  // #ifndef H5
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      header: {
        Authorization: token ? `Bearer ${token}` : ''
      },
      success: (res) => {
        if (res.statusCode === 200) {
          uni.openDocument({ filePath: res.tempFilePath, showMenu: true })
          resolve(res)
        } else {
          reject(res)
        }
      },
      fail: reject
    })
  })
  // #endif
}

/**
 * 保存Base64文件
 * @param {string} base64Data base64编码的数据
 * @param {string} filename 文件名
 */
export function saveBase64File(base64Data, filename) {
  // #ifdef H5
  const link = document.createElement('a')
  link.href = base64Data
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // #endif

  // #ifndef H5
  // 小程序环境下存储到相册等
  const fs = uni.getFileSystemManager()
  const base64 = base64Data.replace(/^data:.*;base64,/, '')
  const filePath = `${uni.env.USER_DATA_PATH}/${filename}`
  fs.writeFile({
    filePath,
    data: base64,
    encoding: 'base64',
    success: () => {
      uni.saveFile({
        tempFilePath: filePath,
        success: (res) => {
          uni.showToast({ title: '保存成功', icon: 'success' })
        }
      })
    }
  })
  // #endif
}
