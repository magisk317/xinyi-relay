use serde::Serialize;
use std::time::Duration;
use tauri::{
    menu::{MenuBuilder, MenuEvent},
    tray::{MouseButton, MouseButtonState, TrayIcon, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, Manager, Runtime,
};

use crate::desktop_i18n::{localized_app_name, localized_menu_label};

const NAVIGATE_EVENT: &str = "desktop://navigate";
const ACTION_EVENT: &str = "desktop://action";

#[derive(Debug, Clone, Serialize)]
struct DesktopNavigatePayload<'a> {
    path: &'a str,
}

#[derive(Debug, Clone, Serialize)]
struct DesktopActionPayload<'a> {
    kind: &'a str,
}

pub(crate) fn show_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn tray_icon_image() -> tauri::image::Image<'static> {
    const TRAY_ICON_BYTES: &[u8] = include_bytes!("../icons/tray-icon.png");
    tauri::image::Image::from_bytes(TRAY_ICON_BYTES)
        .expect("tray icon png bytes must decode into a valid image")
}

fn emit_navigation(app: &AppHandle, path: &str) {
    let app_handle = app.clone();
    let target_path = path.to_string();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_millis(180)).await;
        if let Some(window) = app_handle.get_webview_window("main") {
            let stamp = chrono::Utc::now().timestamp_millis();
            let hash_path = format!("#{}?tray={}", target_path, stamp);
            let serialized_hash = serde_json::to_string(&hash_path).unwrap_or_else(|_| "\"#/overview\"".to_string());
            let script = format!(
                "const nextHash = {serialized_hash}; if (typeof window.__desktopNavigate === 'function') {{ window.__desktopNavigate({target_path:?}, {stamp}); }} if (window.location.hash !== nextHash) {{ window.location.hash = nextHash; }} else {{ window.dispatchEvent(new HashChangeEvent('hashchange')); }}"
            );
            if let Err(error) = window.eval(&script) {
                eprintln!("[tray] window.eval navigate failed for {}: {}", target_path, error);
            }
            println!("[tray] emit navigate -> {}", target_path);
            let _ = window.emit(
                NAVIGATE_EVENT,
                DesktopNavigatePayload {
                    path: target_path.as_str(),
                },
            );
        } else {
            eprintln!("[tray] main window missing while navigating to {}", target_path);
        }
    });
}

fn emit_action(app: &AppHandle, kind: &str) {
    let app_handle = app.clone();
    let action_kind = kind.to_string();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_millis(180)).await;
        if let Some(window) = app_handle.get_webview_window("main") {
            let script = format!(
                "if (typeof window.__desktopAction === 'function') {{ window.__desktopAction({action_kind:?}); }}"
            );
            if let Err(error) = window.eval(&script) {
                eprintln!("[tray] window.eval action failed for {}: {}", action_kind, error);
            }
            println!("[tray] emit action -> {}", action_kind);
            let _ = window.emit(
                ACTION_EVENT,
                DesktopActionPayload {
                    kind: action_kind.as_str(),
                },
            );
        } else {
            eprintln!("[tray] main window missing while sending action {}", action_kind);
        }
    });
}

fn handle_menu_event(app: &AppHandle, event: &MenuEvent) {
    println!("[tray] menu clicked -> {}", event.id().as_ref());
    match event.id().as_ref() {
        "show" => show_main_window(app),
        "overview" => {
            show_main_window(app);
            emit_navigation(app, "/overview");
        }
        "devices" => {
            show_main_window(app);
            emit_navigation(app, "/devices");
        }
        "records" => {
            show_main_window(app);
            emit_navigation(app, "/records");
        }
        "restart-monitor" => {
            crate::restart_monitor(app);
            show_main_window(app);
            emit_action(app, "restart-monitor");
        }
        "quit" => app.exit(0),
        _ => {}
    }
}

pub(crate) fn setup_tray(app: &AppHandle, language_tag: &str) -> tauri::Result<()> {
    let menu = MenuBuilder::new(app)
        .text("show", localized_menu_label(language_tag, "show"))
        .text("overview", localized_menu_label(language_tag, "overview"))
        .text("devices", localized_menu_label(language_tag, "devices"))
        .text("records", localized_menu_label(language_tag, "records"))
        .text(
            "restart-monitor",
            localized_menu_label(language_tag, "restart-monitor"),
        )
        .separator()
        .text("quit", localized_menu_label(language_tag, "quit"))
        .build()?;

    let tray_icon = TrayIconBuilder::with_id("relay-tray")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .tooltip(localized_app_name(language_tag))
        .icon(
            Some(tray_icon_image()).or_else(|| app.default_window_icon().cloned())
                .expect("tray icon or default window icon must be available"),
        )
        .on_menu_event(|app, event: MenuEvent| handle_menu_event(app, &event))
        .on_tray_icon_event(|tray: &TrayIcon<_>, event: TrayIconEvent| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                println!("[tray] left click -> show main window");
                show_main_window(tray.app_handle());
            }
        })
        .build(app)?;

    if let Some(window) = app.get_webview_window("main") {
        let _ = window.set_title(localized_app_name(language_tag));
    }

    app.manage(tray_icon);
    Ok(())
}
