package org.viptv.app

import android.app.Activity
import android.os.Bundle
import android.view.TextureView

/** Included only by the explicit private fixture artifact property. */
class OwnedNativeFixtureActivity : Activity() {
    lateinit var texture: TextureView
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texture = TextureView(this)
        setContentView(texture)
    }
}
