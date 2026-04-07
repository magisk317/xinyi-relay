#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::{
    menu::MenuEvent,
    menu::MenuBuilder,
    tray::{MouseButton, MouseButtonState, TrayIcon, TrayIconBuilder, TrayIconEvent},
    AppHandle, Manager, Runtime, WindowEvent,
};

fn show_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.unminimize();
        let _ = window.set_focus();
    }
}

fn hide_main_window<R: Runtime>(app: &AppHandle<R>) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.hide();
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .setup(|app| {
            let menu = MenuBuilder::new(app)
                .text("show", "Show Console")
                .text("hide", "Hide Window")
                .separator()
                .text("quit", "Quit")
                .build()?;

            let tray_icon = TrayIconBuilder::with_id("relay-tray")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .tooltip("Xinyi Relay Desktop")
                .icon(
                    app.default_window_icon()
                        .cloned()
                        .expect("default window icon must be available"),
                )
                .on_menu_event(|app, event: MenuEvent| match event.id().as_ref() {
                    "show" => show_main_window(app),
                    "hide" => hide_main_window(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray: &TrayIcon<_>, event: TrayIconEvent| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        show_main_window(tray.app_handle());
                    }
                })
                .build(app)?;

            app.manage(tray_icon);
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() != "main" {
                return;
            }
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running xinyi relay desktop");
}
