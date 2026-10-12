export function showSuccessToast(title = '操作成功', duration = 1500) {
  uni.showToast({
    title,
    icon: 'success',
    duration
  })
}

export function showErrorToast(title = '操作失败', duration = 2500) {
  uni.showToast({
    title,
    icon: 'none',
    duration
  })
}

export function showWarningToast(title = '警告', duration = 2500) {
  uni.showToast({
    title,
    icon: 'none',
    duration
  })
}

export function showInfoToast(title = '提示', duration = 2000) {
  uni.showToast({
    title,
    icon: 'none',
    duration
  })
}

export function showLoadingToast(title = '加载中...') {
  uni.showLoading({
    title
  })
}

export function hideLoadingToast() {
  uni.hideLoading()
}

export function showModal(title, content, confirmText = '确定', cancelText = '取消') {
  return new Promise((resolve, reject) => {
    uni.showModal({
      title,
      content,
      confirmText,
      cancelText,
      success: (res) => {
        if (res.confirm) {
          resolve(true)
        } else {
          resolve(false)
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

export function showActionSheet(itemList, itemColor = '#000000') {
  return new Promise((resolve, reject) => {
    uni.showActionSheet({
      itemList,
      itemColor,
      success: (res) => {
        resolve(res.tapIndex)
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

export default {
  showSuccessToast,
  showErrorToast,
  showWarningToast,
  showInfoToast,
  showLoadingToast,
  hideLoadingToast,
  showModal,
  showActionSheet
}
