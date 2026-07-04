import { reportError } from '@/api/errorLog'
import { getToken } from './token'

let errorQueue: any[] = []
let isReporting = false
const REPORT_INTERVAL = 5000
const MAX_QUEUE_SIZE = 50

let frontendTraceId = generateTraceId()
let currentPage = window.location.pathname

function generateTraceId(): string {
  return 'fe-' + Date.now().toString(36) + '-' + Math.random().toString(36).substring(2, 10)
}

export function getFrontendTraceId(): string {
  return frontendTraceId
}

export function setCurrentPage(page: string) {
  currentPage = page
}

function addErrorToQueue(error: any) {
  if (errorQueue.length >= MAX_QUEUE_SIZE) {
    errorQueue.shift()
  }
  errorQueue.push({
    ...error,
    timestamp: Date.now(),
    traceId: frontendTraceId,
    page: currentPage,
  })
  scheduleReport()
}

function scheduleReport() {
  if (isReporting) return
  setTimeout(() => {
    reportQueue()
  }, REPORT_INTERVAL)
}

async function reportQueue() {
  if (isReporting || errorQueue.length === 0) return
  isReporting = true
  const errors = [...errorQueue]
  errorQueue = []
  try {
    for (const err of errors) {
      try {
        await reportError(err)
      } catch {
      }
    }
  } finally {
    isReporting = false
    if (errorQueue.length > 0) {
      scheduleReport()
    }
  }
}

export function setupErrorHandler() {
  const originalOnError = window.onerror
  window.onerror = function(message, source, lineno, colno, error) {
    try {
      addErrorToQueue({
        level: 'error',
        source: 'frontend',
        message: String(message),
        stackTrace: error?.stack || `at ${source}:${lineno}:${colno}`,
        url: window.location.href,
      })
    } catch {
    }
    if (originalOnError) {
      return originalOnError.call(window, message, source, lineno, colno, error)
    }
    return false
  }

  window.addEventListener('unhandledrejection', (event) => {
    try {
      const reason = event.reason
      addErrorToQueue({
        level: 'error',
        source: 'frontend',
        message: reason?.message || String(reason),
        stackTrace: reason?.stack,
        url: window.location.href,
      })
    } catch {
    }
  })

  if (console && console.error) {
    const originalError = console.error
    console.error = function(...args: any[]) {
      try {
        const message = args.map(a => {
          if (typeof a === 'string') return a
          if (a instanceof Error) return a.message
          try { return JSON.stringify(a) } catch { return String(a) }
        }).join(' ')
        addErrorToQueue({
          level: 'warn',
          source: 'frontend',
          message: message.substring(0, 500),
          url: window.location.href,
        })
      } catch {
      }
      originalError.apply(console, args)
    }
  }
}

export function logFrontendError(error: Error | string, level: string = 'error') {
  try {
    const isError = error instanceof Error
    addErrorToQueue({
      level,
      source: 'frontend',
      message: isError ? error.message : error,
      stackTrace: isError ? error.stack : undefined,
      url: window.location.href,
    })
  } catch {
  }
}

export function logFrontendInfo(message: string, extra?: Record<string, any>) {
  try {
    addErrorToQueue({
      level: 'info',
      source: 'frontend',
      message: message.substring(0, 500),
      url: window.location.href,
      ...extra,
    })
  } catch {
  }
}

export function logFrontendWarn(message: string, extra?: Record<string, any>) {
  try {
    addErrorToQueue({
      level: 'warn',
      source: 'frontend',
      message: message.substring(0, 500),
      url: window.location.href,
      ...extra,
    })
  } catch {
  }
}

export function logPageView(path: string, title?: string) {
  try {
    setCurrentPage(path)
    addErrorToQueue({
      level: 'info',
      source: 'frontend',
      message: `页面访问: ${path}`,
      url: window.location.href,
      pageTitle: title || document.title,
      pagePath: path,
      action: 'page_view',
    })
  } catch {
  }
}

export function logUserAction(action: string, target: string, extra?: Record<string, any>) {
  try {
    addErrorToQueue({
      level: 'info',
      source: 'frontend',
      message: `用户操作: ${action} - ${target}`,
      url: window.location.href,
      action,
      target,
      ...extra,
    })
  } catch {
  }
}
