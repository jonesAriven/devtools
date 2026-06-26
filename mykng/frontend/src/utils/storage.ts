const PREFIX = 'mykng_'

export const storage = {
  get<T>(key: string): T | null {
    const val = localStorage.getItem(PREFIX + key)
    if (val === null) return null
    try {
      return JSON.parse(val) as T
    } catch {
      return val as unknown as T
    }
  },

  set(key: string, value: any): void {
    const val = typeof value === 'string' ? value : JSON.stringify(value)
    localStorage.setItem(PREFIX + key, val)
  },

  remove(key: string): void {
    localStorage.removeItem(PREFIX + key)
  },

  clear(): void {
    Object.keys(localStorage)
      .filter((k) => k.startsWith(PREFIX))
      .forEach((k) => localStorage.removeItem(k))
  },
}
