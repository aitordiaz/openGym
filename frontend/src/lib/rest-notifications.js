import { MOBILE } from './mobile.js'
import { t } from './i18n.js'

export const clock = sec => Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0')

let restTimerPlugin = null

async function getRestTimerPlugin() {
  if (!MOBILE) return null
  if (restTimerPlugin) return restTimerPlugin
  try {
    const { registerPlugin } = await import('@capacitor/core')
    restTimerPlugin = registerPlugin('RestTimer')
    return restTimerPlugin
  } catch {
    return null
  }
}

export async function setRestTimer(timer) {
  if (!timer || !(timer.endsAt > Date.now())) return
  if (MOBILE) {
    try {
      const plugin = await getRestTimerPlugin()
      if (!plugin) return
      await plugin.setRestTimer({
        endsAt: timer.endsAt,
        title: t('Rest timer'),
        body: t('Rest'),
        add15Label: '+15s',
        skipLabel: t('Skip'),
        finishedTitle: t('Rest over — next set!'),
        finishedBody: 'openGym'
      })
    } catch {}
  }
}

export async function showRestNotification(timer) {
  if (!timer || !(timer.endsAt > Date.now())) return

  if (MOBILE) {
    try {
      const plugin = await getRestTimerPlugin()
      if (!plugin) return
      await plugin.show({
        endsAt: timer.endsAt,
        title: t('Rest timer'),
        body: t('Rest'),
        add15Label: '+15s',
        skipLabel: t('Skip'),
        finishedTitle: t('Rest over — next set!'),
        finishedBody: 'openGym'
      })
    } catch {
      // Best effort fallback
    }
    return
  }

  // Web / PWA notification
  if (typeof Notification !== 'undefined' && Notification.permission === 'granted' && typeof navigator !== 'undefined') {
    const left = Math.max(0, Math.round((timer.endsAt - Date.now()) / 1000))
    const body = `${clock(left)} (${t('Rest')})`
    const payload = {
      type: 'SHOW_REST_NOTIFICATION',
      title: 'openGym — ' + t('Rest timer'),
      body,
      add15Label: '+15s',
      skipLabel: t('Skip'),
      endsAt: timer.endsAt
    }

    try {
      if (navigator.serviceWorker?.controller) {
        navigator.serviceWorker.controller.postMessage(payload)
      } else if (navigator.serviceWorker?.getRegistration) {
        const reg = await navigator.serviceWorker.getRegistration()
        if (reg?.showNotification) {
          await reg.showNotification(payload.title, {
            body: payload.body,
            icon: 'icon-512.png',
            badge: 'icon-180.png',
            tag: 'rest-timer-ongoing',
            renotify: false,
            actions: [
              { action: 'add15', title: payload.add15Label },
              { action: 'skip', title: payload.skipLabel }
            ]
          })
        }
      }
    } catch {
      // Ignore if not supported in current environment
    }
  }
}

export async function dismissRestNotification() {
  if (MOBILE) {
    try {
      const plugin = await getRestTimerPlugin()
      if (plugin?.dismiss) await plugin.dismiss()
    } catch {}
    return
  }

  if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
    try {
      if (navigator.serviceWorker.controller) {
        navigator.serviceWorker.controller.postMessage({ type: 'CLEAR_REST_NOTIFICATION' })
      }
      const reg = await navigator.serviceWorker.getRegistration?.()
      if (reg?.getNotifications) {
        const list = await reg.getNotifications({ tag: 'rest-timer-ongoing' })
        for (const n of list) n.close()
      }
    } catch {}
  }
}

export async function clearRestNotification() {
  if (MOBILE) {
    try {
      const plugin = await getRestTimerPlugin()
      if (plugin) await plugin.clear()
    } catch {}
    return
  }

  await dismissRestNotification()
}

export async function syncRestTimerFromNative(store) {
  if (!MOBILE) return
  try {
    const plugin = await getRestTimerPlugin()
    if (!plugin) return
    const state = await plugin.getState()
    if (!state) return

    if (state.isSkipped) {
      store.getState().stopRest()
      return
    }

    const current = store.getState().timer
    if (current && state.endsAt && Math.abs(state.endsAt - current.endsAt) >= 1000) {
      const endsAt = Math.round(state.endsAt)
      const left = Math.max(0, Math.round((endsAt - Date.now()) / 1000))
      if (left <= 0) {
        store.getState().stopRest()
      } else {
        store.setState({ timer: { ...current, endsAt, left } })
      }
    }
  } catch {}
}

let listenersSetup = false
export function setupRestNotificationListeners(store) {
  if (listenersSetup) return
  listenersSetup = true

  if (MOBILE) {
    getRestTimerPlugin().then(plugin => {
      if (!plugin) return
      plugin.addListener('timerAdjusted', data => {
        if (data?.added) {
          store.getState().addRest(data.added)
        } else if (data?.endsAt) {
          const current = store.getState().timer
          if (current) {
            const endsAt = Math.round(data.endsAt)
            const left = Math.max(0, Math.round((endsAt - Date.now()) / 1000))
            store.setState({ timer: { ...current, endsAt, left } })
          }
        }
      })
      plugin.addListener('timerSkipped', () => {
        store.getState().stopRest()
      })
      plugin.addListener('timerFinished', () => {
        store.getState().stopRest()
      })
    }).catch(() => {})
  } else if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
    navigator.serviceWorker.addEventListener('message', e => {
      if (e.data?.type === 'REST_TIMER_ADD') {
        store.getState().addRest(e.data.sec || 15)
      } else if (e.data?.type === 'REST_TIMER_SKIP') {
        store.getState().stopRest()
      }
    })
  }
}

export async function requestNotificationPermission() {
  if (MOBILE) {
    try {
      const { LocalNotifications } = await import('@capacitor/local-notifications')
      const status = await LocalNotifications.checkPermissions()
      if (status.display !== 'granted') {
        await LocalNotifications.requestPermissions()
      }
    } catch {
      try {
        const plugin = await getRestTimerPlugin()
        if (plugin?.requestPermissions) {
          await plugin.requestPermissions()
        }
      } catch {}
    }
    return
  }

  if (typeof Notification !== 'undefined' && typeof Notification.requestPermission === 'function') {
    try {
      if (Notification.permission === 'default') {
        await Notification.requestPermission()
      }
    } catch {}
  }
}

let initialized = false
export function initRestNotifications(store) {
  if (initialized || typeof document === 'undefined') return
  initialized = true

  setupRestNotificationListeners(store)
  requestNotificationPermission().catch(() => {})

  const onHidden = () => {
    const timer = store.getState().timer
    if (timer && timer.left > 0 && timer.endsAt > Date.now()) {
      showRestNotification(timer)
    }
  }

  const onVisible = () => {
    dismissRestNotification()
    syncRestTimerFromNative(store)
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      onHidden()
    } else {
      onVisible()
    }
  })

  if (MOBILE) {
    import('@capacitor/app').then(({ App }) => {
      App.addListener('appStateChange', ({ isActive }) => {
        if (!isActive) {
          onHidden()
        } else {
          onVisible()
        }
      })
    }).catch(() => {})
  }
}
