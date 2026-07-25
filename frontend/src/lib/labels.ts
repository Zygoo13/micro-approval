export const statusLabel = (value: string) => ({
  PENDING: 'Chờ xử lý',
  IN_REVIEW: 'Đang review',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  COMPLETED: 'Hoàn tất',
}[value] ?? value)

export const modeLabel = (value: string) => ({
  RAW_SNIPPET: 'Code snippet',
  INTENT_MATCHING: 'So khớp yêu cầu',
  GIT_DIFF: 'Git diff',
}[value] ?? value)

export const aiLabel = (value: string) => ({
  SUCCEEDED: 'AI đã phân tích',
  FALLBACK: 'Rule fallback',
  DISABLED: 'Chỉ Rule Engine',
  NOT_REQUESTED: 'AI không cần chạy',
}[value] ?? value)
