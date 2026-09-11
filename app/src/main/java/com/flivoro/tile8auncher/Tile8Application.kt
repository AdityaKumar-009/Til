package com.flivoro.tile8auncher

import android.app.Application

/**
 * Process application for Mosaic Launcher.
 *
 * Unlock rendering deliberately lives in MainActivity's single Compose/window surface. Keeping a
 * second full-screen ComposeView above the launcher proved unsafe on some OEM compositors: the
 * secondary surface could be restored independently of the Activity content and obscure the real
 * Start screen. The Activity now owns both the wallpaper-only sleep state and the entrance gate.
 */
class Tile8Application : Application()
