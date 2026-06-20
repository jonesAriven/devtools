/** 统一上下文路径（从 .env VITE_CONTEXT_PATH 读取，唯一派生点） */
export const CONTEXT_PATH = import.meta.env.VITE_CONTEXT_PATH || '/kb'
