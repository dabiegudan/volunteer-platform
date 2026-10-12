/**
 * 图表工具函数
 * 提供数据格式化和图表导出等通用功能
 */

/**
 * 格式化图表数据（标准 {name, value} 格式）
 * @param {Array} data 原始数据
 * @param {string} nameKey 名称字段
 * @param {string} valueKey 值字段
 * @returns {Array} 格式化后的数据
 */
export function formatChartData(data, nameKey = 'name', valueKey = 'value') {
  if (!data || !Array.isArray(data)) return []
  return data.map(item => ({
    name: item[nameKey],
    value: Number(item[valueKey]) || 0
  }))
}

/**
 * 格式化折线图数据
 * @param {Array} data 原始数据
 * @param {string} dateKey 日期字段
 * @param {string} valueKey 值字段
 * @returns {Array} 格式化后的数据
 */
export function formatLineData(data, dateKey = 'date', valueKey = 'count') {
  if (!data || !Array.isArray(data)) return []
  return data.map(item => ({
    date: item[dateKey],
    value: Number(item[valueKey]) || 0
  }))
}

/**
 * 下载图表为图片
 * @param {Object} chartInstance ECharts实例
 * @param {string} filename 文件名
 */
export function downloadChartImage(chartInstance, filename = 'chart.png') {
  if (!chartInstance) return

  const url = chartInstance.getDataURL({
    type: 'png',
    pixelRatio: 2,
    backgroundColor: '#fff'
  })

  // #ifdef H5
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  // #endif

  // #ifndef H5
  uni.showToast({
    title: '图片已生成',
    icon: 'success'
  })
  // #endif
}

/**
 * 获取图表默认颜色
 */
export const CHART_COLORS = [
  '#4CAF50', '#FF9800', '#00BCD4', '#E91E63',
  '#9C27B0', '#3F51B5', '#FF5722', '#009688',
  '#795548', '#607D8B'
]
