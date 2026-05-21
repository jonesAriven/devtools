use std::fs::OpenOptions;
use std::io::Write;
use std::path::PathBuf;
use std::sync::Mutex;

use chrono::Local;

static LOG_PATH: Mutex<Option<PathBuf>> = Mutex::new(None);

fn get_log_path() -> PathBuf {
    let mut guard = LOG_PATH.lock().unwrap();
    if guard.is_none() {
        let exe_dir = std::env::current_exe()
            .unwrap_or_else(|_| PathBuf::from("."))
            .parent()
            .unwrap_or_else(|| std::path::Path::new("."))
            .to_path_buf();
        *guard = Some(exe_dir.join("qrcode_tool.log"));
    }
    guard.clone().unwrap()
}

pub fn log(message: &str) {
    let path = get_log_path();
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(&path) {
        let timestamp = Local::now().format("%Y-%m-%d %H:%M:%S");
        let _ = writeln!(file, "[{}] {}", timestamp, message);
    }
}
