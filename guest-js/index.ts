import { invoke } from '@tauri-apps/api/core'

export async function startScan(value: string) {
  await invoke<{ value?: string }>('plugin:provision|startScan', {})
}
