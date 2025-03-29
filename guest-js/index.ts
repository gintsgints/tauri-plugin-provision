import { invoke } from '@tauri-apps/api/core'

export type BleDevice = {
  address: string;
  name: string;
}

export async function startScan() {
  await invoke<{ value?: string }>('plugin:provision|startScan', {})
}
