use std::fs::{self, File, OpenOptions};
use std::io::{self, BufWriter, Write};
use std::sync::Mutex;
use chrono::Local;
use tauri::Manager;

/// 全局日志写入器
static LOG_WRITER: Mutex<Option<BufWriter<File>>> = Mutex::new(None);

/// 初始化日志文件
pub fn init_log_file(app_handle: &tauri::AppHandle) -> io::Result<()> {
    let log_dir = app_handle
        .path()
        .app_log_dir()
        .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;

    // 确保日志目录存在
    fs::create_dir_all(&log_dir)?;

    // 生成日志文件名（按日期）
    let now = Local::now();
    let log_filename = format!("xinyi-relay-{}.log", now.format("%Y-%m-%d"));
    let log_path = log_dir.join(log_filename);

    // 打开日志文件（追加模式）
    let file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)?;

    let writer = BufWriter::new(file);

    // 设置全局日志写入器
    let mut global_writer = LOG_WRITER.lock().map_err(|e| {
        io::Error::new(io::ErrorKind::Other, format!("Failed to lock log writer: {}", e))
    })?;
    *global_writer = Some(writer);

    // 输出日志文件路径
    println!("Log file initialized: {:?}", log_path);

    Ok(())
}

/// 写入日志到文件
pub fn write_log(level: &str, message: &str) {
    let now = Local::now();
    let timestamp = now.format("%Y-%m-%d %H:%M:%S%.3f");

    // 格式化日志行
    let log_line = format!("[{}] [{}] {}\n", timestamp, level, message);

    // 尝试写入全局日志写入器
    if let Ok(mut writer_guard) = LOG_WRITER.lock() {
        if let Some(ref mut writer) = *writer_guard {
            let _ = writer.write_all(log_line.as_bytes());
            let _ = writer.flush();
        }
    }
}

/// 日志宏
#[macro_export]
macro_rules! log_debug {
    ($($arg:tt)*) => {
        {
            let message = format!($($arg)*);
            println!("[DEBUG] {}", message);
            $crate::logger::write_log("DEBUG", &message);
        }
    };
}

#[macro_export]
macro_rules! log_info {
    ($($arg:tt)*) => {
        {
            let message = format!($($arg)*);
            println!("[INFO] {}", message);
            $crate::logger::write_log("INFO", &message);
        }
    };
}

#[macro_export]
macro_rules! log_warn {
    ($($arg:tt)*) => {
        {
            let message = format!($($arg)*);
            eprintln!("[WARN] {}", message);
            $crate::logger::write_log("WARN", &message);
        }
    };
}

#[macro_export]
macro_rules! log_error {
    ($($arg:tt)*) => {
        {
            let message = format!($($arg)*);
            eprintln!("[ERROR] {}", message);
            $crate::logger::write_log("ERROR", &message);
        }
    };
}
