import { invoke, addPluginListener, PluginListener } from '@tauri-apps/api/core'

export type BleDevice = {
  address: string;
  name: string;
}

export async function onDevice(
  handler: (device: BleDevice) => void
): Promise<PluginListener> {
  return addPluginListener('provision', 'onDevice', handler)
}

export async function startScan() {
  await invoke<{ value?: string }>('plugin:provision|startScan', {})
}
