// @vitest-environment happy-dom
import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import {
  clock,
  showRestNotification,
  clearRestNotification,
  syncRestTimerFromNative,
  initRestNotifications
} from './rest-notifications.js'

describe('rest-notifications', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('clock formats minutes and seconds correctly', () => {
    expect(clock(0)).toBe('0:00')
    expect(clock(9)).toBe('0:09')
    expect(clock(65)).toBe('1:05')
    expect(clock(120)).toBe('2:00')
    expect(clock(3605)).toBe('60:05')
  })

  it('showRestNotification ignores null or expired timer', async () => {
    // Should not throw or do anything
    await showRestNotification(null)
    await showRestNotification({ endsAt: Date.now() - 1000 })
  })

  it('clearRestNotification handles non-mobile environment safely', async () => {
    await clearRestNotification()
  })

  it('syncRestTimerFromNative safely handles non-mobile environment', async () => {
    const store = {
      getState: () => ({ timer: null }),
      setState: vi.fn()
    }
    await syncRestTimerFromNative(store)
    expect(store.setState).not.toHaveBeenCalled()
  })

  it('initializes visibility listeners and handles hide/show lifecycle', async () => {
    const stopRest = vi.fn()
    const addRest = vi.fn()
    const store = {
      getState: () => ({
        timer: { left: 90, total: 90, endsAt: Date.now() + 90000 },
        stopRest,
        addRest
      }),
      setState: vi.fn()
    }

    initRestNotifications(store)

    // Simulate going hidden
    Object.defineProperty(document, 'hidden', { value: true, configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))

    // Simulate going visible
    Object.defineProperty(document, 'hidden', { value: false, configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })

  it('requestNotificationPermission runs safely in web environment', async () => {
    const { requestNotificationPermission } = await import('./rest-notifications.js')
    await requestNotificationPermission()
  })
})
