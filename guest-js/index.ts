import { invoke, addPluginListener, PluginListener } from '@tauri-apps/api/core'

export type BleDevice = {
  address: string;
  name: string;
  error: string;
}

export type ProvisionRequest = {
  address: string;
  pop: string;
  ssid: string;
  password: string;
}

export async function onDevice(
  handler: (device: BleDevice) => void
): Promise<PluginListener> {
  return addPluginListener('provision', 'onDevice', handler)
}

export async function startScan() {
  await invoke<{ value?: string }>('plugin:provision|startScan', {})
}

export async function wifiProvision(req: ProvisionRequest) {
  await invoke<{ value?: string }>('plugin:provision|wifiProvision', req)
}