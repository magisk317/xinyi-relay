pub(crate) fn system_language_tag() -> String {
    let raw = std::env::var("LC_ALL")
        .ok()
        .filter(|value| !value.trim().is_empty())
        .or_else(|| {
            std::env::var("LC_MESSAGES")
                .ok()
                .filter(|value| !value.trim().is_empty())
        })
        .or_else(|| std::env::var("LANG").ok().filter(|value| !value.trim().is_empty()))
        .unwrap_or_else(|| "en".to_string())
        .to_lowercase();

    if raw.starts_with("zh_tw") || raw.starts_with("zh-tw") || raw.starts_with("zh_hk") || raw.starts_with("zh-hk") {
        "zh-TW".to_string()
    } else if raw.starts_with("zh") {
        "zh-CN".to_string()
    } else {
        "en".to_string()
    }
}

pub(crate) fn is_chinese_language(language_tag: &str) -> bool {
    language_tag.starts_with("zh")
}

pub(crate) fn localized_app_name(language_tag: &str) -> &'static str {
    if is_chinese_language(language_tag) {
        "信驿 Relay Desktop"
    } else {
        "Xinyi Relay Desktop"
    }
}

pub(crate) fn localized_brand_name(language_tag: &str) -> &'static str {
    if is_chinese_language(language_tag) {
        "信驿 Relay"
    } else {
        "Xinyi Relay"
    }
}

pub(crate) fn localized_menu_label(language_tag: &str, key: &str) -> &'static str {
    if is_chinese_language(language_tag) {
        match key {
            "show" => "显示控制台",
            "overview" => "打开概览",
            "devices" => "打开设备",
            "records" => "打开记录",
            "restart-monitor" => "重新连接监控",
            "quit" => "退出",
            _ => "",
        }
    } else {
        match key {
            "show" => "Show Console",
            "overview" => "Open Overview",
            "devices" => "Open Devices",
            "records" => "Open Records",
            "restart-monitor" => "Reconnect Monitor",
            "quit" => "Quit",
            _ => "",
        }
    }
}

pub(crate) fn localized_runtime_text(language_tag: &str, key: &str) -> &'static str {
    if is_chinese_language(language_tag) {
        match key {
            "initializing" => "正在初始化桌面端...",
            "no_active_profile" => "未选择激活后端。",
            "backend_connected" => "后端已连接",
            "backend_state_changed" => "后端状态已变化",
            "device_update" => "设备变更",
            "device_updated" => "设备已更新",
            "device_revoked" => "设备已吊销",
            "record_batch_received" => "收到新记录",
            "device_update_body" => "有新设备出现，或已有设备重新恢复到桌面控制台中。",
            "device_updated_body" => "已有设备的桌面元数据发生了变化。",
            "device_revoked_body" => "有设备被吊销，或已经从当前活动设备列表中移除。",
            _ => "",
        }
    } else {
        match key {
            "initializing" => "Initializing desktop runtime...",
            "no_active_profile" => "No active backend profile selected.",
            "backend_connected" => "Backend connected",
            "backend_state_changed" => "Backend state changed",
            "device_update" => "Device update",
            "device_updated" => "Device updated",
            "device_revoked" => "Device revoked",
            "record_batch_received" => "Record batch received",
            "device_update_body" => "A new or restored device appeared in the desktop console.",
            "device_updated_body" => "A registered device changed its desktop metadata.",
            "device_revoked_body" => "A device was revoked or removed from the active device set.",
            _ => "",
        }
    }
}

pub(crate) fn localized_backend_login_hint(language_tag: &str, profile_name: &str) -> String {
    if is_chinese_language(language_tag) {
        format!("后端 {} 可达。请先登录以解锁控制台能力。", profile_name)
    } else {
        format!("Backend reachable at {}. Sign in to unlock console features.", profile_name)
    }
}

pub(crate) fn localized_connected_summary(
    language_tag: &str,
    profile_name: &str,
    user_count: i64,
    app_env: &str,
) -> String {
    if is_chinese_language(language_tag) {
        format!("已连接到 {} · {} 个用户 · {}", profile_name, user_count, app_env)
    } else {
        format!("Connected to {} · {} users · {}", profile_name, user_count, app_env)
    }
}

pub(crate) fn localized_record_summary(
    language_tag: &str,
    record_type: &str,
    sender: &str,
    sms_code: &str,
) -> String {
    if sms_code.is_empty() {
        if is_chinese_language(language_tag) {
            format!("收到来自 {} 的 {} 记录", sender, record_type)
        } else {
            format!("New {} record from {}", record_type, sender)
        }
    } else if is_chinese_language(language_tag) {
        format!("收到来自 {} 的验证码 {}", sender, sms_code)
    } else {
        format!("New verification code {} from {}", sms_code, sender)
    }
}

pub(crate) fn localized_runtime_message(language_tag: &str, key: &str) -> &'static str {
    if is_chinese_language(language_tag) {
        match key {
            "profile_required" => "名称和后端地址不能为空。",
            "profile_not_found" => "未找到后端配置。",
            "no_active_backend" => "暂无激活后端。",
            "login_required" => "当前后端需要先完成桌面登录。",
            "signed_out" => "已退出登录，后端配置仍然保留。",
            _ => "",
        }
    } else {
        match key {
            "profile_required" => "Profile name and backend URL are required.",
            "profile_not_found" => "Profile not found.",
            "no_active_backend" => "No active backend profile.",
            "login_required" => "Desktop login required for this backend.",
            "signed_out" => "Signed out. Backend profile is still available.",
            _ => "",
        }
    }
}

pub(crate) fn localized_probe_success_message(language_tag: &str, base_url: &str) -> String {
    if is_chinese_language(language_tag) {
        format!("后端 {} 可达", base_url)
    } else {
        format!("Backend reachable at {}", base_url)
    }
}

pub(crate) fn localized_connected_message(language_tag: &str, profile_name: &str) -> String {
    if is_chinese_language(language_tag) {
        format!("已连接到 {}", profile_name)
    } else {
        format!("Connected to {}", profile_name)
    }
}
